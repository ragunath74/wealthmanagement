CREATE TABLE advisor (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT uq_advisor_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE security (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    wash_sale_replacement_symbol VARCHAR(32) NULL,
    last_price DECIMAL(19,6) NOT NULL,
    last_price_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_symbol UNIQUE (symbol)
) ENGINE=InnoDB;

CREATE TABLE model_portfolio (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE model_portfolio_target (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    model_portfolio_id VARCHAR(36) NOT NULL,
    security_id VARCHAR(36) NOT NULL,
    target_weight DECIMAL(9,6) NOT NULL,
    CONSTRAINT uq_model_target UNIQUE (model_portfolio_id, security_id),
    CONSTRAINT fk_target_model FOREIGN KEY (model_portfolio_id) REFERENCES model_portfolio (id),
    CONSTRAINT fk_target_security FOREIGN KEY (security_id) REFERENCES security (id)
) ENGINE=InnoDB;

CREATE TABLE client_account (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    advisor_id VARCHAR(36) NOT NULL,
    model_portfolio_id VARCHAR(36) NULL,
    cash_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    tax_loss_harvesting_enabled BIT NOT NULL DEFAULT 1,
    taxable BIT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_account_advisor FOREIGN KEY (advisor_id) REFERENCES advisor (id),
    CONSTRAINT fk_account_model FOREIGN KEY (model_portfolio_id) REFERENCES model_portfolio (id)
) ENGINE=InnoDB;

CREATE INDEX idx_account_model ON client_account (model_portfolio_id);
CREATE INDEX idx_account_advisor ON client_account (advisor_id);

CREATE TABLE tax_lot (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    security_id VARCHAR(36) NOT NULL,
    acquired_date DATE NOT NULL,
    quantity DECIMAL(19,6) NOT NULL,
    cost_basis_per_share DECIMAL(19,6) NOT NULL,
    closed BIT NOT NULL DEFAULT 0,
    closed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lot_account FOREIGN KEY (account_id) REFERENCES client_account (id),
    CONSTRAINT fk_lot_security FOREIGN KEY (security_id) REFERENCES security (id)
) ENGINE=InnoDB;

CREATE INDEX idx_lot_account_security ON tax_lot (account_id, security_id);
CREATE INDEX idx_lot_open ON tax_lot (account_id, security_id, closed);

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
