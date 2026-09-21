package com.wealthtech.rebalance.driftengine.web.dto;

import com.wealthtech.rebalance.driftengine.domain.TradeOrder;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeOrderResponse(
        String id,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal referencePrice,
        String reason,
        String status,
        Instant createdAt
) {
    public static TradeOrderResponse from(TradeOrder order) {
        return new TradeOrderResponse(
                order.getId(), order.getSymbol(), order.getSide(), order.getQuantity(),
                order.getReferencePrice(), order.getReason(), order.getStatus().name(), order.getCreatedAt());
    }
}
