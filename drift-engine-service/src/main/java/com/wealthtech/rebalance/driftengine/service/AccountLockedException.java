package com.wealthtech.rebalance.driftengine.service;

/** Thrown when another process already holds the per-account rebalance lock. Retriable -- the Kafka listener's error handler redelivers after a backoff. */
public class AccountLockedException extends RuntimeException {
    public AccountLockedException(String accountId) {
        super("Account " + accountId + " is currently being rebalanced by another process");
    }
}
