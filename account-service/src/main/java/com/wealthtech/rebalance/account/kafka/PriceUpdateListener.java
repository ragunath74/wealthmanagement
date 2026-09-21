package com.wealthtech.rebalance.account.kafka;

import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.common.event.PriceUpdateEvent;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code market.price.updates} in its OWN consumer group ({@code account-service}) so it
 * gets a full independent copy of the stream -- drift-engine-service subscribes to the same topic
 * under a different group id purely to decide whether to fan out a rebalance, which is classic
 * Kafka pub/sub: one topic, N independent consumer groups, each sees every message.
 */
@Component
public class PriceUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateListener.class);
    private static final String CONSUMER_NAME = "account-service-price-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final SecurityRepository securityRepository;

    public PriceUpdateListener(ObjectMapper objectMapper, IdempotencyService idempotencyService, SecurityRepository securityRepository) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.securityRepository = securityRepository;
    }

    @KafkaListener(topics = KafkaTopics.MARKET_PRICE_UPDATES, groupId = CONSUMER_NAME)
    @Transactional
    public void onMessage(String json) {
        PriceUpdateEvent event = objectMapper.readValue(json, PriceUpdateEvent.class);
        if (!idempotencyService.tryClaim(event.eventId(), CONSUMER_NAME)) {
            log.debug("Duplicate price update event {} ignored", event.eventId());
            return;
        }
        Security security = securityRepository.findBySymbol(event.symbol())
                .orElseThrow(() -> new IllegalArgumentException("Unknown symbol " + event.symbol()));
        security.setLastPrice(event.price());
        security.setLastPriceAt(event.observedAt());
        securityRepository.save(security);
    }
}
