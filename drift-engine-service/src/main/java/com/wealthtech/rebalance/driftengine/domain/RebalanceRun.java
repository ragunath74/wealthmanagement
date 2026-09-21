package com.wealthtech.rebalance.driftengine.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** Audit record of one account-level rebalance evaluation, whether or not it produced trades. Required for compliance/SEC review. */
@Entity
@Table(name = "rebalance_run", indexes = @Index(name = "idx_run_account", columnList = "account_id"))
public class RebalanceRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String triggerType; // PRICE_UPDATE | MODEL_UPDATE

    @Column(nullable = false)
    private Instant startedAt = Instant.now();

    private Instant completedAt;

    @Column(nullable = false)
    private int tradesGenerated = 0;

    @Column(nullable = false)
    private boolean skipped = false;

    private String skipReason;

    private String errorMessage;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getTradesGenerated() {
        return tradesGenerated;
    }

    public void setTradesGenerated(int tradesGenerated) {
        this.tradesGenerated = tradesGenerated;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
