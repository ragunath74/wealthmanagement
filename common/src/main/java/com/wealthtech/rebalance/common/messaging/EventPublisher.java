package com.wealthtech.rebalance.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Thin JSON-over-Kafka helper. Every service that publishes events depends on this instead of hand-rolling serialization. */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String key, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topic, key, json);
        } catch (JacksonException e) {
            log.error("Failed to serialize event for topic {} key {}", topic, key, e);
            throw new IllegalStateException("Event serialization failure", e);
        }
    }

    public void publishRaw(String topic, String key, String json) {
        kafkaTemplate.send(topic, key, json);
    }
}
