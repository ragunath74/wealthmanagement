package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;
import java.util.List;

public record DriftReport(
        BigDecimal totalMarketValue,
        BigDecimal cashBalance,
        List<SecurityDrift> perSecurity
) {
}
