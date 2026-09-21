package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;

public record LotSaleAllocation(
        LotView lot,
        BigDecimal quantity,
        BigDecimal realizedGainLoss,
        boolean longTerm
) {
}
