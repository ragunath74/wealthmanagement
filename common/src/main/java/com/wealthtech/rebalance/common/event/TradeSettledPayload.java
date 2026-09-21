package com.wealthtech.rebalance.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Written to execution-gateway-service's outbox in the same transaction as its execution-record
 * row, then relayed to {@code trade.settled}. Two independent consumer groups read this same
 * event: account-service applies the position/cash mutation (the saga's completing step, done
 * asynchronously and eventually-consistently rather than in the same transaction as the original
 * trade decision), and drift-engine-service marks its TradeOrder row FILLED for the audit trail.
 */
public record TradeSettledPayload(
        String orderId,
        String accountId,
        String securityId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal fillPrice,
        String idempotencyKey,
        Instant filledAt,
        List<LotAllocationPayload> lotAllocations
) {
}
