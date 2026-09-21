package com.wealthtech.rebalance.common.event;

import java.math.BigDecimal;

/** How much of a specific tax lot a SELL order draws down. Carried end-to-end from the trade decision (drift-engine-service) through execution to settlement (account-service) so every service applies the SAME lot allocation the decision was made against -- nobody re-derives it. */
public record LotAllocationPayload(
        String lotId,
        BigDecimal quantity,
        BigDecimal realizedGainLoss
) {
}
