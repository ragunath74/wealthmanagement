package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;
import java.util.List;

public record GeneratedTrade(
        SecurityView security,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal referencePrice,
        String reason,
        List<LotSaleAllocation> lotAllocations // populated for SELL only
) {
    public BigDecimal notional() {
        return quantity.multiply(referencePrice);
    }
}
