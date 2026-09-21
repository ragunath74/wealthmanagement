package com.wealthtech.rebalance.common.outbox;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Transactional outbox row: written in the SAME database transaction that makes the business
 * decision it describes (a trade decision in drift-engine-service, a fill in
 * execution-gateway-service). A separate, generic {@link OutboxRelayService} polls unsent rows
 * and publishes them to Kafka, then marks them sent -- so "the decision was persisted" and "the
 * decision was published" can never disagree, at the cost of at-least-once delivery. Every
 * consumer of a topic this feeds must be idempotent (see {@code idempotency} package).
 */
@Entity
@Table(name = "outbox_event", indexes = @Index(name = "idx_outbox_unsent", columnList = "sent_at"))
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String topic;

    /** Used as the Kafka message key so all events for one aggregate land on the same partition, preserving order. */
    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant sentAt;

    @Column(nullable = false)
    private int attempts = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }
}
