package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;

public record SecurityDrift(
        SecurityView security,
        BigDecimal currentQuantity,
        BigDecimal currentWeight,
        BigDecimal targetWeight,
        BigDecimal driftBps,
        BigDecimal dollarDelta
) {
    public boolean exceedsThreshold(BigDecimal thresholdBps) {
        return driftBps.abs().compareTo(thresholdBps) >= 0;
    }
}
