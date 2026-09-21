CREATE TABLE trade_order (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    security_id VARCHAR(36) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity DECIMAL(19,6) NOT NULL,
    reference_price DECIMAL(19,6) NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_order_idempotency UNIQUE (idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX idx_order_account ON trade_order (account_id);
CREATE INDEX idx_order_status ON trade_order (status);

CREATE TABLE rebalance_run (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    started_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6) NULL,
    trades_generated INT NOT NULL DEFAULT 0,
    skipped BIT NOT NULL DEFAULT 0,
    skip_reason VARCHAR(64) NULL,
    error_message VARCHAR(1000) NULL
) ENGINE=InnoDB;

CREATE INDEX idx_run_account ON rebalance_run (account_id);

-- Shared building blocks from the common module (see com.wealthtech.rebalance.common.outbox / .idempotency)
CREATE TABLE outbox_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    sent_at TIMESTAMP(6) NULL,
    attempts INT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE INDEX idx_outbox_unsent ON outbox_event (sent_at);

CREATE TABLE processed_event (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    consumer VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;
