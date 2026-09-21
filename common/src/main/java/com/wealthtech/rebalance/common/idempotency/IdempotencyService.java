package com.wealthtech.rebalance.common.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    /**
     * Atomically claims an event id for a given consumer. Runs in its own REQUIRES_NEW
     * transaction so the claim commits (or is discovered as a duplicate) independently of the
     * caller's larger business transaction, and so a duplicate-key failure here never poisons
     * that outer transaction.
     *
     * @return true if this call claimed the event (caller should process it); false if it was
     *         already processed (caller should skip it).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaim(String eventId, String consumer) {
        if (processedEventRepository.existsById(eventId)) {
            return false;
        }
        try {
            ProcessedEvent event = new ProcessedEvent();
            event.setEventId(eventId);
            event.setConsumer(consumer);
            processedEventRepository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
