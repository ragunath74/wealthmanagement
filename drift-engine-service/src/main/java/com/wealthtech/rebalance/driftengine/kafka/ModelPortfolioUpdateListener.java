package com.wealthtech.rebalance.driftengine.kafka;

import com.wealthtech.rebalance.common.event.ModelPortfolioUpdatedEvent;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.driftengine.service.RebalanceDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ModelPortfolioUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(ModelPortfolioUpdateListener.class);
    private static final String CONSUMER_NAME = "drift-engine-model-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final RebalanceDispatchService rebalanceDispatchService;

    public ModelPortfolioUpdateListener(ObjectMapper objectMapper, IdempotencyService idempotencyService, RebalanceDispatchService rebalanceDispatchService) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.rebalanceDispatchService = rebalanceDispatchService;
    }

    @KafkaListener(topics = KafkaTopics.MODEL_PORTFOLIO_UPDATES, groupId = CONSUMER_NAME)
    public void onMessage(String json) {
        ModelPortfolioUpdatedEvent event = objectMapper.readValue(json, ModelPortfolioUpdatedEvent.class);
        if (!idempotencyService.tryClaim(event.eventId(), CONSUMER_NAME)) {
            log.debug("Duplicate model update event {} ignored", event.eventId());
            return;
        }
        rebalanceDispatchService.dispatchForModelUpdate(event.modelPortfolioId(), event.revision());
    }
}
