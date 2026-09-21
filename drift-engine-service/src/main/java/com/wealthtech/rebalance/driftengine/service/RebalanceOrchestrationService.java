package com.wealthtech.rebalance.driftengine.service;

import com.wealthtech.rebalance.common.event.AccountRebalanceRequested;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.lock.DistributedLockService;
import com.wealthtech.rebalance.driftengine.config.RebalanceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Entry point for processing one {@link AccountRebalanceRequested} event. Three layers of
 * protection against duplicate/concurrent processing, in order:
 *   1. {@link DistributedLockService} -- a Redis lock per account, so two instances (or a
 *      redelivered message racing the original) never run the decision+persistence pipeline for
 *      the same account concurrently.
 *   2. {@link IdempotencyService} -- claims the event id in drift-engine-service's own
 *      processed_event table, so a redelivered Kafka message is a guaranteed no-op.
 *   3. The unique DB constraint on {@code TradeOrder.idempotencyKey} (see
 *      RebalanceTransactionService) -- the backstop even if the first two somehow failed.
 */
@Service
public class RebalanceOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceOrchestrationService.class);

    private final DistributedLockService distributedLockService;
    private final IdempotencyService idempotencyService;
    private final RebalanceDecisionService rebalanceDecisionService;
    private final RebalanceTransactionService rebalanceTransactionService;
    private final RebalanceProperties rebalanceProperties;

    public RebalanceOrchestrationService(DistributedLockService distributedLockService,
                                          IdempotencyService idempotencyService,
                                          RebalanceDecisionService rebalanceDecisionService,
                                          RebalanceTransactionService rebalanceTransactionService,
                                          RebalanceProperties rebalanceProperties) {
        this.distributedLockService = distributedLockService;
        this.idempotencyService = idempotencyService;
        this.rebalanceDecisionService = rebalanceDecisionService;
        this.rebalanceTransactionService = rebalanceTransactionService;
        this.rebalanceProperties = rebalanceProperties;
    }

    public void process(AccountRebalanceRequested event) {
        String lockKey = "rebalance:account:" + event.accountId();
        Optional<DistributedLockService.Lock> lock = distributedLockService.tryLock(
                lockKey, Duration.ofSeconds(rebalanceProperties.lockWaitSeconds()));

        if (lock.isEmpty()) {
            throw new AccountLockedException(event.accountId());
        }
        try {
            if (!idempotencyService.tryClaim(event.eventId(), "account-rebalance-consumer")) {
                log.debug("Skipping already-processed event {}", event.eventId());
                return;
            }
            RebalanceDecisionService.Decision decision = rebalanceDecisionService.decide(event.accountId());
            int trades = rebalanceTransactionService.persistDecision(event.accountId(), event.triggerType(), event.eventId(), decision.trades());
            log.info("Rebalanced account {} (trigger={}, ref={}) -> {} trades", event.accountId(), event.triggerType(), event.triggerRef(), trades);
        } finally {
            distributedLockService.unlock(lock.get());
        }
    }
}
