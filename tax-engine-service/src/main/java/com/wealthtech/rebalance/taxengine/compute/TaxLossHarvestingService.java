package com.wealthtech.rebalance.taxengine.compute;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Identifies unrealized-loss positions worth harvesting, subject to the IRC section 1091 wash
 * sale rule: a loss is disallowed if the same or a "substantially identical" security was bought
 * within the 30 days before OR after the loss sale. This engine can't see the future, so it
 * enforces the rule conservatively in both directions:
 *   (1) BEFORE selling: skip harvesting a security if any lot of it was acquired within the
 *       wash-sale window, because that recent purchase would disallow all or part of the loss.
 *   (2) AFTER selling: the caller (drift-engine-service, via this service's trade list) must not
 *       repurchase the same security within the window -- {@link TradeGenerationService} enforces
 *       that by substituting {@code washSaleReplacementSymbol} to preserve market exposure.
 *
 * Being stateless, this service is handed the resolved replacement securities on the request
 * (keyed by symbol) rather than looking them up itself -- it has no database to look them up in.
 */
@Service
public class TaxLossHarvestingService {

    private final TaxLotSelectionService taxLotSelectionService;

    public TaxLossHarvestingService(TaxLotSelectionService taxLotSelectionService) {
        this.taxLotSelectionService = taxLotSelectionService;
    }

    public List<HarvestCandidate> findHarvestCandidates(List<LotView> openLots,
                                                          int washSaleWindowDays,
                                                          BigDecimal minHarvestLoss,
                                                          LocalDate asOf,
                                                          Map<String, SecurityView> replacementBySymbol) {
        Map<SecurityView, List<LotView>> lotsBySecurity = new LinkedHashMap<>();
        for (LotView lot : openLots) {
            lotsBySecurity.computeIfAbsent(lot.security(), s -> new ArrayList<>()).add(lot);
        }

        List<HarvestCandidate> candidates = new ArrayList<>();
        LocalDate washSaleCutoff = asOf.minusDays(washSaleWindowDays);

        for (Map.Entry<SecurityView, List<LotView>> entry : lotsBySecurity.entrySet()) {
            SecurityView security = entry.getKey();
            List<LotView> lots = entry.getValue();

            boolean recentlyPurchased = lots.stream().anyMatch(lot -> lot.acquiredDate().isAfter(washSaleCutoff));
            if (recentlyPurchased) {
                continue; // see rule (1) above
            }

            BigDecimal lossQuantity = lots.stream()
                    .filter(lot -> security.lastPrice().compareTo(lot.costBasisPerShare()) < 0)
                    .map(LotView::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lossQuantity.signum() <= 0) {
                continue;
            }

            List<LotSaleAllocation> allocations = taxLotSelectionService.selectLotsForSale(
                    lots.stream().filter(lot -> security.lastPrice().compareTo(lot.costBasisPerShare()) < 0).toList(),
                    lossQuantity, security.lastPrice(), LotSelectionStrategy.HIFO, asOf);

            BigDecimal totalLoss = allocations.stream().map(LotSaleAllocation::realizedGainLoss)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalLoss.abs().compareTo(minHarvestLoss) < 0) {
                continue;
            }

            SecurityView replacement = security.washSaleReplacementSymbol() == null || security.washSaleReplacementSymbol().isBlank()
                    ? null
                    : replacementBySymbol.get(security.washSaleReplacementSymbol());

            candidates.add(new HarvestCandidate(security, allocations, totalLoss, replacement));
        }
        return candidates;
    }
}
