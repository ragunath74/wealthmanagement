package com.wealthtech.rebalance.executiongateway.kafka;

import com.wealthtech.rebalance.common.event.TradeOrderPublishedPayload;
import com.wealthtech.rebalance.common.event.TradeSettledPayload;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.common.outbox.OutboxEvent;
import com.wealthtech.rebalance.common.outbox.OutboxEventRepository;
import com.wealthtech.rebalance.executiongateway.domain.ExecutionRecord;
import com.wealthtech.rebalance.executiongateway.domain.ExecutionStatus;
import com.wealthtech.rebalance.executiongateway.repository.ExecutionRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Consumes {@code trade.orders.outbound}, stands in for a FIX 4.4/5.0 order-routing adapter
 * (logs what a real NewOrderSingle -> ExecutionReport exchange would look like, then simulates an
 * immediate fill at the reference price), and republishes {@code trade.settled} via its OWN
 * transactional outbox -- this is the second outbox in the platform (drift-engine-service's being
 * the first), reinforcing that this is a general reusable pattern, not a one-off.
 */
@Component
public class TradeOrderListener {

    private static final Logger log = LoggerFactory.getLogger(TradeOrderListener.class);
    private static final String CONSUMER_NAME = "execution-gateway-order-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final ExecutionRecordRepository executionRecordRepository;
    private final OutboxEventRepository outboxEventRepository;

    public TradeOrderListener(ObjectMapper objectMapper,
                               IdempotencyService idempotencyService,
                               ExecutionRecordRepository executionRecordRepository,
                               OutboxEventRepository outboxEventRepository) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.executionRecordRepository = executionRecordRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @KafkaListener(topics = KafkaTopics.TRADE_ORDERS_OUTBOUND, groupId = CONSUMER_NAME)
    @Transactional
    public void onMessage(String json) {
        TradeOrderPublishedPayload order = objectMapper.readValue(json, TradeOrderPublishedPayload.class);
        if (!idempotencyService.tryClaim(order.idempotencyKey(), CONSUMER_NAME)) {
            log.debug("Duplicate order {} ignored", order.orderId());
            return;
        }

        log.info("FIX 4.4 NewOrderSingle -> ExecutionReport(FILLED) for order {} ({} {} {} @ {})",
                order.orderId(), order.side(), order.quantity(), order.symbol(), order.referencePrice());

        Instant filledAt = Instant.now();

        ExecutionRecord record = new ExecutionRecord();
        record.setOrderId(order.orderId());
        record.setAccountId(order.accountId());
        record.setSecurityId(order.securityId());
        record.setSymbol(order.symbol());
        record.setSide(order.side());
        record.setQuantity(order.quantity());
        record.setFillPrice(order.referencePrice()); // simulated fill at reference price -- a real gateway would report the actual execution price
        record.setStatus(ExecutionStatus.FILLED);
        record.setIdempotencyKey(order.idempotencyKey());
        record.setFilledAt(filledAt);
        executionRecordRepository.save(record);

        TradeSettledPayload settled = new TradeSettledPayload(
                order.orderId(), order.accountId(), order.securityId(), order.symbol(), order.side(),
                order.quantity(), order.referencePrice(), order.idempotencyKey(), filledAt, order.lotAllocations());

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic(KafkaTopics.TRADE_SETTLED);
        outboxEvent.setAggregateId(order.accountId());
        outboxEvent.setEventType("TRADE_SETTLED");
        outboxEvent.setPayload(objectMapper.writeValueAsString(settled));
        outboxEventRepository.save(outboxEvent);
    }
}
