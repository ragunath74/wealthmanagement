-- ============================================================================
-- Portfolio Rebalancing Platform -- full MySQL schema (all services)
-- ============================================================================
--
-- Each microservice owns its own database. This is the "shared instance,
-- separate schemas" compromise: one MySQL server for local/demo convenience
-- (the user's request), but no service is ever given credentials to another
-- service's database, and no service's JPA/Hibernate mapping references a
-- table outside its own schema. In production these would typically be
-- separate database instances/clusters, one per service, so a noisy-neighbor
-- workload on one service can never starve another's connection pool.
--
-- HOW TO USE THIS FILE
--   Option A (recommended) -- let Flyway manage the schema:
--     Run ONLY the three `CREATE DATABASE` statements below, then start each
--     service once; each service's own Flyway migration
--     (src/main/resources/db/migration/V1__*.sql) creates its own tables on
--     first boot, and every later `mvnw spring-boot:run` just validates the
--     schema is still in sync (spring.jpa.hibernate.ddl-auto=validate).
--
--   Option B -- run this whole file by hand (e.g. you want the tables to
--     exist before you ever start a service, or you're doing this for
--     interview prep and want to read/run the DDL yourself):
--     Run the entire file, then start every service with Flyway disabled
--     (SPRING_FLYWAY_ENABLED=false, or spring.flyway.enabled=false), since
--     Flyway does not know these tables were created outside of it and will
--     otherwise fail on startup trying to re-run V1 against a database that
--     already has the tables.
--
--   Run with:  mysql -u root -p < sql/full-schema.sql
--
-- ============================================================================


-- ============================================================================
-- Database: account_service
-- Owns: advisors, securities, model portfolios, client accounts, tax lots.
-- Consumers of its gRPC API: drift-engine-service.
-- Consumers of market.price.updates / trade.settled (Kafka): itself.
-- ============================================================================
CREATE DATABASE IF NOT EXISTS account_service CHARACTER SET utf8mb4;
USE account_service;

CREATE TABLE IF NOT EXISTS advisor (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    CONSTRAINT uq_advisor_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS security (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    wash_sale_replacement_symbol VARCHAR(32) NULL,
    last_price DECIMAL(19,6) NOT NULL,
    last_price_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_security_symbol UNIQUE (symbol)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS model_portfolio (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS model_portfolio_target (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    model_portfolio_id VARCHAR(36) NOT NULL,
    security_id VARCHAR(36) NOT NULL,
    target_weight DECIMAL(9,6) NOT NULL,
    CONSTRAINT uq_model_target UNIQUE (model_portfolio_id, security_id),
    CONSTRAINT fk_target_model FOREIGN KEY (model_portfolio_id) REFERENCES model_portfolio (id),
    CONSTRAINT fk_target_security FOREIGN KEY (security_id) REFERENCES security (id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS client_account (
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

CREATE TABLE IF NOT EXISTS tax_lot (
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

-- Reusable building blocks (see com.wealthtech.rebalance.common.outbox / .idempotency).
-- Every service that writes an outbox row or consumes a Kafka event has its own copy
-- of these two tables -- they are intentionally NOT shared across databases.
CREATE TABLE IF NOT EXISTS outbox_event (
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

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    consumer VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;


-- ============================================================================
-- Database: drift_engine_service
-- Owns: trade decisions (trade_order), rebalance audit trail (rebalance_run).
-- Callers: none (it's the orchestrator) -- it calls OUT to account-service and
-- tax-engine-service over gRPC, and consumes/produces Kafka topics.
-- ============================================================================
CREATE DATABASE IF NOT EXISTS drift_engine_service CHARACTER SET utf8mb4;
USE drift_engine_service;

CREATE TABLE IF NOT EXISTS trade_order (
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

CREATE TABLE IF NOT EXISTS rebalance_run (
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

CREATE TABLE IF NOT EXISTS outbox_event (
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

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    consumer VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;


-- ============================================================================
-- Database: execution_gateway_service
-- Owns: execution_record (the "order is being worked" state a real FIX engine
-- would track). tax-engine-service, by contrast, has NO database at all --
-- it's a stateless gRPC compute node (see tax-engine-service/pom.xml).
-- ============================================================================
CREATE DATABASE IF NOT EXISTS execution_gateway_service CHARACTER SET utf8mb4;
USE execution_gateway_service;

CREATE TABLE IF NOT EXISTS execution_record (
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

CREATE TABLE IF NOT EXISTS outbox_event (
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

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY,
    consumer VARCHAR(128) NOT NULL,
    processed_at TIMESTAMP(6) NOT NULL
) ENGINE=InnoDB;
