package com.wealthtech.rebalance.taxengine.compute;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DriftCalculationServiceTest {

    private final DriftCalculationService service = new DriftCalculationService();

    private LotView lot(SecurityView security, String qty, String basis) {
        return new LotView("lot-" + security.symbol(), security, new BigDecimal(qty), new BigDecimal(basis), LocalDate.now().minusYears(2));
    }

    @Test
    void accountExactlyAtTargetHasZeroDrift() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        SecurityView bnd = new SecurityView("s2", "BND", new BigDecimal("50"), null);

        List<LotView> lots = List.of(lot(vti, "6", "90"), lot(bnd, "8", "45")); // $600 + $400 = $1000, 60/40
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("0.6")), new ModelTargetView(bnd, new BigDecimal("0.4")));

        DriftReport report = service.calculateDrift(BigDecimal.ZERO, lots, targets);

        assertThat(report.totalMarketValue()).isEqualByComparingTo("1000");
        for (SecurityDrift drift : report.perSecurity()) {
            assertThat(drift.driftBps()).isCloseTo(BigDecimal.ZERO, Offset.offset(new BigDecimal("0.01")));
        }
    }

    @Test
    void overweightPositionProducesNegativeDollarDelta() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        List<LotView> lots = List.of(lot(vti, "10", "80"));
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("0.5")));

        DriftReport report = service.calculateDrift(BigDecimal.ZERO, lots, targets);
        SecurityDrift drift = report.perSecurity().get(0);

        assertThat(drift.currentWeight()).isEqualByComparingTo("1.0");
        assertThat(drift.dollarDelta()).isNegative();
        assertThat(drift.exceedsThreshold(BigDecimal.valueOf(500))).isTrue();
    }

    @Test
    void unheldTargetSecurityProducesPositiveDollarDelta() {
        SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);
        SecurityView bnd = new SecurityView("s2", "BND", new BigDecimal("50"), null);
        List<LotView> lots = List.of(lot(vti, "10", "80"));
        List<ModelTargetView> targets = List.of(new ModelTargetView(vti, new BigDecimal("0.5")), new ModelTargetView(bnd, new BigDecimal("0.5")));

        DriftReport report = service.calculateDrift(BigDecimal.ZERO, lots, targets);
        SecurityDrift bondDrift = report.perSecurity().stream().filter(d -> d.security().symbol().equals("BND")).findFirst().orElseThrow();

        assertThat(bondDrift.currentQuantity()).isEqualByComparingTo("0");
        assertThat(bondDrift.dollarDelta()).isPositive();
    }
}
