package com.wealthtech.rebalance.common.kafka;

/**
 * The complete topic list for the platform. Each topic's comment records who produces it, who
 * consumes it, and why -- see also kafka/create-topics.sh for the partition-count rationale per
 * topic and ARCHITECTURE.md section 4 for the full design discussion.
 */
public final class KafkaTopics {

    /** Producer: account-service (price simulation endpoint). Consumers: account-service (own group, updates Security.lastPrice) AND drift-engine-service (own group, debounced dispatch trigger). Two independent consumer groups on one topic = classic pub/sub fan-out. */
    public static final String MARKET_PRICE_UPDATES = "market.price.updates";

    /** Producer: account-service (PUT /model-portfolios/{id}/targets). Consumer: drift-engine-service (dispatch fan-out). */
    public static final String MODEL_PORTFOLIO_UPDATES = "model.portfolio.updates";

    /** Producer: drift-engine-service (dispatch). Consumer: drift-engine-service (same service, horizontally-scaled consumer group). Partitioned by accountId -- this is the parallelism budget for the whole platform. */
    public static final String ACCOUNT_REBALANCE_REQUESTED = "account.rebalance.requested";

    /** Producer: drift-engine-service (outbox relay, after a trade decision commits). Consumer: execution-gateway-service. */
    public static final String TRADE_ORDERS_OUTBOUND = "trade.orders.outbound";

    /** Producer: execution-gateway-service (outbox relay, after simulating a fill). Consumers: account-service (mutate positions -- the saga's compensating/completing step) AND drift-engine-service (mark the TradeOrder FILLED). Again two independent consumer groups on one topic. */
    public static final String TRADE_SETTLED = "trade.settled";

    private KafkaTopics() {
    }
}
