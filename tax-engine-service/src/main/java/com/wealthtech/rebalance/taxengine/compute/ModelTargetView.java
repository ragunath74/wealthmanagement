package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;

public record ModelTargetView(
        SecurityView security,
        BigDecimal targetWeight
) {
}
