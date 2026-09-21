CREATE TABLE execution_record (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    security_id VARCHAR(36) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    quantity DECIMAL(19,6) NOT NULL,
    fill_price DECIMAL(19,6) NOT NULL,
    status VARCHAR(24) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    filled_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_execution_order UNIQUE (order_id)
) ENGINE=InnoDB;

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
