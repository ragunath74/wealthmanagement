package com.wealthtech.rebalance.taxengine.compute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TradeGenerationServiceTest {

    private final TradeGenerationService service = new TradeGenerationService(
            new DriftCalculationService(), new TaxLossHarvestingService(new TaxLotSelectionService()), new TaxLotSelectionService());

    private final TradeGenerationService.RebalancePolicy policy = new TradeGenerationService.RebalancePolicy(
            BigDecimal.valueOf(500), BigDecimal.valueOf(10), BigDecimal.valueOf(0.0), 30, BigDecimal.valueOf(50), LotSelectionStrategy.TAX_OPTIMAL);

    private LotView lot(SecurityView security, String qty, String basis) {
        return new LotView("lot-" + security.symbol(), security, new BigDecimal(qty), new BigDecimal(basis), LocalDate.now().minusYears(2));
    }

    @Test
    void underweightPositionWithSufficientCashGeneratesBuy() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("1.0")));

        List<GeneratedTrade> trades = service.generateTrades(true, false, new BigDecimal("1000"),
                List.of(), targets, Map.of(), policy, LocalDate.now());

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).side()).isEqualTo(TradeSide.BUY);
        assertThat(trades.get(0).security().symbol()).isEqualTo("VTI");
    }

    @Test
    void overweightPositionGeneratesSell() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        List<LotView> lots = List.of(lot(vti, "10", "80")); // 100% VTI, target 50%
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("0.5")));

        List<GeneratedTrade> trades = service.generateTrades(true, false, BigDecimal.ZERO,
                lots, targets, Map.of(), policy, LocalDate.now());

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).side()).isEqualTo(TradeSide.SELL);
    }

    @Test
    void buysAreScaledDownWhenCashInsufficient() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        SecurityView bnd = new SecurityView("s2", "BND", new BigDecimal("100"), null);
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("0.5")), new ModelTargetView(bnd, new BigDecimal("0.5")));

        List<GeneratedTrade> trades = service.generateTrades(true, false, new BigDecimal("30"),
                List.of(), targets, Map.of(), policy, LocalDate.now());

        BigDecimal totalBuyNotional = trades.stream().filter(t -> t.side() == TradeSide.BUY)
                .map(GeneratedTrade::notional).reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(trades).isNotEmpty();
        assertThat(totalBuyNotional).isLessThanOrEqualTo(new BigDecimal("30"));
    }

    @Test
    void tradesBelowMinimumAmountAreFiltered() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("1.0")));

        List<GeneratedTrade> trades = service.generateTrades(true, false, new BigDecimal("5"),
                List.of(), targets, Map.of(), policy, LocalDate.now());

        assertThat(trades).isEmpty();
    }

    @Test
    void withinDriftThresholdProducesNoTrades() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        List<LotView> lots = List.of(lot(vti, "6", "90")); // $600, target 100% matches exactly
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("1.0")));

        List<GeneratedTrade> trades = service.generateTrades(true, false, BigDecimal.ZERO,
                lots, targets, Map.of(), policy, LocalDate.now());

        assertThat(trades).isEmpty();
    }
}
