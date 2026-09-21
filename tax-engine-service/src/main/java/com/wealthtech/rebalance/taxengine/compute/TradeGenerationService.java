package com.wealthtech.rebalance.taxengine.compute;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Orchestrates drift calculation, tax-loss harvesting and cash constraints into the final list of
 * BUY/SELL orders for one account -- this is the compute core behind
 * {@code TaxOptimizationService.GenerateTrades}, matching the reference architecture's "Tax &amp;
 * Optimization Service" node (wash-sale compliance, gain minimization, transaction/cash limits).
 */
@Service
public class TradeGenerationService {

    private static final int QUANTITY_SCALE = 4;

    private final DriftCalculationService driftCalculationService;
    private final TaxLossHarvestingService taxLossHarvestingService;
    private final TaxLotSelectionService taxLotSelectionService;

    public TradeGenerationService(DriftCalculationService driftCalculationService,
                                   TaxLossHarvestingService taxLossHarvestingService,
                                   TaxLotSelectionService taxLotSelectionService) {
        this.driftCalculationService = driftCalculationService;
        this.taxLossHarvestingService = taxLossHarvestingService;
        this.taxLotSelectionService = taxLotSelectionService;
    }

    public record RebalancePolicy(
            BigDecimal driftThresholdBps,
            BigDecimal minTradeAmount,
            BigDecimal cashBufferPct,
            int washSaleWindowDays,
            BigDecimal minHarvestLoss,
            LotSelectionStrategy lotSelectionStrategy
    ) {
    }

    public List<GeneratedTrade> generateTrades(boolean taxable,
                                                boolean taxLossHarvestingEnabled,
                                                BigDecimal cashBalance,
                                                List<LotView> openLots,
                                                List<ModelTargetView> modelTargets,
                                                Map<String, SecurityView> replacementBySymbol,
                                                RebalancePolicy policy,
                                                LocalDate asOf) {
        List<GeneratedTrade> trades = new ArrayList<>();
        Set<String> securitiesHandledByHarvest = new HashSet<>();

        if (taxable && taxLossHarvestingEnabled) {
            for (HarvestCandidate candidate : taxLossHarvestingService.findHarvestCandidates(
                    openLots, policy.washSaleWindowDays(), policy.minHarvestLoss(), asOf, replacementBySymbol)) {
                trades.addAll(buildHarvestTrades(candidate));
                securitiesHandledByHarvest.add(candidate.security().id());
                if (candidate.replacementSecurity() != null) {
                    securitiesHandledByHarvest.add(candidate.replacementSecurity().id());
                }
            }
        }

        DriftReport driftReport = driftCalculationService.calculateDrift(cashBalance, openLots, modelTargets);
        LotSelectionStrategy strategy = taxable ? policy.lotSelectionStrategy() : LotSelectionStrategy.FIFO;

        for (SecurityDrift drift : driftReport.perSecurity()) {
            SecurityView security = drift.security();
            if (securitiesHandledByHarvest.contains(security.id())) {
                continue; // already traded this run by the harvest step; don't double-trade the same name
            }
            if (!drift.exceedsThreshold(policy.driftThresholdBps())) {
                continue;
            }
            if (security.lastPrice().signum() <= 0) {
                continue; // stale/unpriced security -- skip rather than trade blind
            }

            BigDecimal quantity = drift.dollarDelta().abs().divide(security.lastPrice(), QUANTITY_SCALE, RoundingMode.DOWN);
            if (quantity.signum() <= 0) {
                continue;
            }

            if (drift.dollarDelta().signum() > 0) {
                trades.add(new GeneratedTrade(security, TradeSide.BUY, quantity, security.lastPrice(), "DRIFT_REBALANCE", List.of()));
            } else {
                BigDecimal sellable = quantity.min(drift.currentQuantity());
                if (sellable.signum() <= 0) continue;
                List<LotView> lotsForSecurity = openLots.stream().filter(l -> l.security().id().equals(security.id())).toList();
                List<LotSaleAllocation> allocations = taxLotSelectionService.selectLotsForSale(
                        lotsForSecurity, sellable, security.lastPrice(), strategy, asOf);
                trades.add(new GeneratedTrade(security, TradeSide.SELL, sellable, security.lastPrice(), "DRIFT_REBALANCE", allocations));
            }
        }

        applyMinimumTradeSize(trades, policy.minTradeAmount());
        applyCashConstraint(trades, cashBalance, driftReport.totalMarketValue(), policy.cashBufferPct());
        return trades;
    }

    private List<GeneratedTrade> buildHarvestTrades(HarvestCandidate candidate) {
        List<GeneratedTrade> out = new ArrayList<>();
        BigDecimal quantity = candidate.lotsToSell().stream().map(LotSaleAllocation::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal price = candidate.security().lastPrice();
        out.add(new GeneratedTrade(candidate.security(), TradeSide.SELL, quantity, price, "TAX_LOSS_HARVEST", candidate.lotsToSell()));

        if (candidate.replacementSecurity() != null && candidate.replacementSecurity().lastPrice().signum() > 0) {
            BigDecimal proceeds = quantity.multiply(price);
            BigDecimal replacementQty = proceeds.divide(candidate.replacementSecurity().lastPrice(), QUANTITY_SCALE, RoundingMode.DOWN);
            if (replacementQty.signum() > 0) {
                out.add(new GeneratedTrade(candidate.replacementSecurity(), TradeSide.BUY, replacementQty,
                        candidate.replacementSecurity().lastPrice(), "TAX_LOSS_HARVEST", List.of()));
            }
        }
        return out;
    }

    private void applyMinimumTradeSize(List<GeneratedTrade> trades, BigDecimal minTradeAmount) {
        trades.removeIf(t -> t.notional().compareTo(minTradeAmount) < 0);
    }

    /**
     * Scales down BUY orders so total buy notional never exceeds cash on hand plus this run's
     * SELL proceeds, less the configured cash buffer. Never scales SELLs -- a generated order
     * never asks the account to spend money it doesn't have.
     */
    private void applyCashConstraint(List<GeneratedTrade> trades, BigDecimal cashBalance, BigDecimal totalMarketValue, BigDecimal cashBufferPct) {
        BigDecimal sellProceeds = trades.stream().filter(t -> t.side() == TradeSide.SELL)
                .map(GeneratedTrade::notional).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal buyNotional = trades.stream().filter(t -> t.side() == TradeSide.BUY)
                .map(GeneratedTrade::notional).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal buffer = totalMarketValue.multiply(cashBufferPct);
        BigDecimal availableForBuys = cashBalance.add(sellProceeds).subtract(buffer);

        if (buyNotional.signum() <= 0 || availableForBuys.compareTo(buyNotional) >= 0) {
            return;
        }
        if (availableForBuys.signum() <= 0) {
            trades.removeIf(t -> t.side() == TradeSide.BUY);
            return;
        }
        BigDecimal ratio = availableForBuys.divide(buyNotional, 8, RoundingMode.DOWN);
        for (int i = 0; i < trades.size(); i++) {
            GeneratedTrade t = trades.get(i);
            if (t.side() != TradeSide.BUY) continue;
            BigDecimal scaledQty = t.quantity().multiply(ratio).setScale(QUANTITY_SCALE, RoundingMode.DOWN);
            trades.set(i, new GeneratedTrade(t.security(), t.side(), scaledQty, t.referencePrice(), t.reason(), t.lotAllocations()));
        }
        trades.removeIf(t -> t.side() == TradeSide.BUY && t.quantity().signum() <= 0);
    }
}
