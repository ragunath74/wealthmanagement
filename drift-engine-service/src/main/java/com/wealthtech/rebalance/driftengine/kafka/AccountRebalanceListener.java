package com.wealthtech.rebalance.driftengine.kafka;

import com.wealthtech.rebalance.common.event.AccountRebalanceRequested;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.driftengine.service.RebalanceOrchestrationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * One consumer group instance per drift-engine-service pod. Partition count on
 * {@code account.rebalance.requested} (see kafka/create-topics.sh) is the hard ceiling on how
 * many of these can run in parallel across the fleet -- see ARCHITECTURE.md for partition-count
 * sizing.
 */
@Component
public class AccountRebalanceListener {

    private final ObjectMapper objectMapper;
    private final RebalanceOrchestrationService rebalanceOrchestrationService;

    public AccountRebalanceListener(ObjectMapper objectMapper, RebalanceOrchestrationService rebalanceOrchestrationService) {
        this.objectMapper = objectMapper;
        this.rebalanceOrchestrationService = rebalanceOrchestrationService;
    }

    @KafkaListener(topics = KafkaTopics.ACCOUNT_REBALANCE_REQUESTED, containerFactory = "accountRebalanceContainerFactory")
    public void onMessage(String json) {
        AccountRebalanceRequested event = objectMapper.readValue(json, AccountRebalanceRequested.class);
        rebalanceOrchestrationService.process(event);
    }
}
