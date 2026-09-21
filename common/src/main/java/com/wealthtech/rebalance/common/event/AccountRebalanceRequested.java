package com.wealthtech.rebalance.common.event;

/**
 * Internal fan-out event, one per account, published to {@code account.rebalance.requested}
 * (partitioned by accountId, so one account's requests are always processed in order by the same
 * consumer). This is what makes the platform horizontally scalable: a model update affecting
 * 500,000 accounts becomes 500,000 small, independent, idempotent units of work that any number
 * of drift-engine-service replicas can drain in parallel.
 */
public record AccountRebalanceRequested(
        String eventId,
        String accountId,
        String triggerType, // MODEL_UPDATE | PRICE_UPDATE
        String triggerRef   // modelPortfolioId or symbol
) {
}
