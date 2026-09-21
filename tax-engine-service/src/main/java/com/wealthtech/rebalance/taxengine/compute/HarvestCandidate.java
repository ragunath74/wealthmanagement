package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;
import java.util.List;

public record HarvestCandidate(
        SecurityView security,
        List<LotSaleAllocation> lotsToSell,
        BigDecimal totalLoss,
        SecurityView replacementSecurity
) {
}
