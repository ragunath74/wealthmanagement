package com.wealthtech.rebalance.common.idempotency;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Idempotency ledger for inbound Kafka events. The unique primary key on {@code eventId} is what
 * actually enforces "processed exactly once" -- insert-or-skip happens in its own transaction
 * (see {@link IdempotencyService}), so a redelivered event (consumer-group rebalance, retry after
 * a timeout, broker-level at-least-once semantics) is a guaranteed no-op rather than a duplicate
 * side effect. Every service with a Kafka consumer has its own local table of these (this is
 * intentionally NOT shared across services -- each service's notion of "have I processed this"
 * is local to itself).
 */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    private String eventId;

    @Column(nullable = false)
    private String consumer;

    @Column(nullable = false)
    private Instant processedAt = Instant.now();

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
}
