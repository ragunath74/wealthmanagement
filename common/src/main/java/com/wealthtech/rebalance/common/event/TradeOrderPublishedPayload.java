package com.wealthtech.rebalance.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Written to drift-engine-service's outbox in the same transaction as the TradeOrder row it
 * describes, then relayed to {@code trade.orders.outbound} for execution-gateway-service to pick
 * up. Carries the full lot-allocation detail for SELL orders so nothing downstream has to
 * re-decide which lots were sold.
 */
public record TradeOrderPublishedPayload(
        String orderId,
        String accountId,
        String securityId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal referencePrice,
        String reason,
        String idempotencyKey,
        Instant createdAt,
        List<LotAllocationPayload> lotAllocations
) {
}
