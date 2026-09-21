package com.wealthtech.rebalance.driftengine.kafka;

import com.wealthtech.rebalance.common.event.TradeSettledPayload;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.driftengine.service.RebalanceTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code trade.settled} in its OWN consumer group, independent of account-service's
 * subscription to the same topic -- this one closes the audit trail (marks the TradeOrder
 * FILLED) rather than mutating positions.
 */
@Component
public class TradeSettledListener {

    private static final Logger log = LoggerFactory.getLogger(TradeSettledListener.class);
    private static final String CONSUMER_NAME = "drift-engine-settlement-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final RebalanceTransactionService rebalanceTransactionService;

    public TradeSettledListener(ObjectMapper objectMapper, IdempotencyService idempotencyService, RebalanceTransactionService rebalanceTransactionService) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.rebalanceTransactionService = rebalanceTransactionService;
    }

    @KafkaListener(topics = KafkaTopics.TRADE_SETTLED, groupId = CONSUMER_NAME)
    public void onMessage(String json) {
        TradeSettledPayload event = objectMapper.readValue(json, TradeSettledPayload.class);
        if (!idempotencyService.tryClaim(event.idempotencyKey(), CONSUMER_NAME)) {
            log.debug("Duplicate settlement for order {} ignored", event.orderId());
            return;
        }
        rebalanceTransactionService.markFilled(event.idempotencyKey());
    }
}
