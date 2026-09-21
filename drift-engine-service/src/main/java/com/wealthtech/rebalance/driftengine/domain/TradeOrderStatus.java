package com.wealthtech.rebalance.driftengine.domain;

public enum TradeOrderStatus {
    GENERATED,      // produced by tax-engine-service's decision, persisted, outbox event written
    QUEUED,         // outbox relay handed it to trade.orders.outbound
    FILLED,         // execution-gateway-service settled it (trade.settled consumed)
    REJECTED
}
