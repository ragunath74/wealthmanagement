package com.wealthtech.rebalance.common.outbox;

import com.wealthtech.rebalance.common.messaging.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Generic transactional-outbox relay: any service that writes {@link OutboxEvent} rows in its
 * own business transaction gets a working relay for free by simply having this bean on its
 * classpath (component-scanned from {@code com.wealthtech.rebalance.common}). Polling on a fixed
 * delay -- rather than publishing inline in the write transaction -- keeps the hot write path
 * free of any Kafka I/O or broker-availability dependency: a Kafka outage delays *publication*,
 * never the underlying business transaction.
 */
@Service
public class OutboxRelayService {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);
    private static final int BATCH_SIZE = 200;

    private final OutboxEventRepository outboxEventRepository;
    private final EventPublisher eventPublisher;

    public OutboxRelayService(OutboxEventRepository outboxEventRepository, EventPublisher eventPublisher) {
        this.outboxEventRepository = outboxEventRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "PT2S")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = outboxEventRepository.findUnsentBatch(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        for (OutboxEvent event : batch) {
            try {
                eventPublisher.publishRaw(event.getTopic(), event.getAggregateId(), event.getPayload());
                event.setSentAt(Instant.now());
            } catch (RuntimeException e) {
                event.setAttempts(event.getAttempts() + 1);
                log.warn("Failed to publish outbox event {} (attempt {})", event.getId(), event.getAttempts(), e);
            }
        }
        outboxEventRepository.saveAll(batch);
    }
}
