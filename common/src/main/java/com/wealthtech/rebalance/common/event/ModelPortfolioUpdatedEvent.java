package com.wealthtech.rebalance.common.event;

/** Published on {@code model.portfolio.updates} by account-service whenever a model's targets change. */
public record ModelPortfolioUpdatedEvent(
        String eventId,
        String modelPortfolioId,
        long revision
) {
}
