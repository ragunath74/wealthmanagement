package com.wealthtech.rebalance.taxengine.compute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaxLossHarvestingServiceTest {

    private final TaxLossHarvestingService service = new TaxLossHarvestingService(new TaxLotSelectionService());

    private LotView lot(SecurityView security, String qty, String basis, LocalDate acquired) {
        return new LotView("lot-" + acquired, security, new BigDecimal(qty), new BigDecimal(basis), acquired);
    }

    @Test
    void identifiesLossPositionAboveThresholdAndResolvesReplacement() {
        SecurityView lossy = new SecurityView("s1", "XYZ", new BigDecimal("50"), "XYZ-ALT");
        SecurityView replacement = new SecurityView("s2", "XYZ-ALT", new BigDecimal("20"), null);

        LotView lot = lot(lossy, "10", "100", LocalDate.now().minusYears(2)); // 10 * (50-100) = -500

        List<HarvestCandidate> candidates = service.findHarvestCandidates(
                List.of(lot), 30, new BigDecimal("50"), LocalDate.now(), Map.of("XYZ-ALT", replacement));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).totalLoss()).isEqualByComparingTo("-500");
        assertThat(candidates.get(0).replacementSecurity().symbol()).isEqualTo("XYZ-ALT");
    }

    @Test
    void skipsSecurityWithRecentPurchaseWithinWashSaleWindow() {
        SecurityView lossy = new SecurityView("s1", "XYZ", new BigDecimal("50"), null);
        LotView oldLot = lot(lossy, "10", "100", LocalDate.now().minusYears(2));
        LotView recentLot = lot(lossy, "1", "55", LocalDate.now().minusDays(5));

        List<HarvestCandidate> candidates = service.findHarvestCandidates(
                List.of(oldLot, recentLot), 30, new BigDecimal("50"), LocalDate.now(), Map.of());

        assertThat(candidates).isEmpty();
    }

    @Test
    void skipsLossBelowMinimumThreshold() {
        SecurityView lossy = new SecurityView("s1", "XYZ", new BigDecimal("99"), null);
        LotView lot = lot(lossy, "1", "100", LocalDate.now().minusYears(2));

        List<HarvestCandidate> candidates = service.findHarvestCandidates(
                List.of(lot), 30, new BigDecimal("50"), LocalDate.now(), Map.of());

        assertThat(candidates).isEmpty();
    }
}
