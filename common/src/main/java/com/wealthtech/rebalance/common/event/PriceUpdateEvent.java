package com.wealthtech.rebalance.common.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published on {@code market.price.updates}, keyed by symbol. Stands in for a real market-data feed adapter. */
public record PriceUpdateEvent(
        String eventId,
        String symbol,
        BigDecimal price,
        Instant observedAt
) {
}
