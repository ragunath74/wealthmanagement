package com.wealthtech.rebalance.driftengine.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * drift-engine-service's own record of a trade decision. This is intentionally NOT the same row
 * account-service mutates positions from -- this table is the decision/audit trail; position
 * truth lives in account-service's tax_lot table, updated later via {@code trade.settled}.
 */
@Entity
@Table(name = "trade_order", indexes = {
        @Index(name = "idx_order_account", columnList = "account_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_idempotency", columnList = "idempotency_key", unique = true)
})
public class TradeOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String accountId;

    @Column(nullable = false)
    private String securityId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String side; // BUY | SELL

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal referencePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TradeOrderStatus status = TradeOrderStatus.GENERATED;

    @Column(nullable = false)
    private String reason; // DRIFT_REBALANCE | TAX_LOSS_HARVEST

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

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

    public String getSecurityId() {
        return securityId;
    }

    public void setSecurityId(String securityId) {
        this.securityId = securityId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSide() {
        return side;
    }

    public void setSide(String side) {
        this.side = side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getReferencePrice() {
        return referencePrice;
    }

    public void setReferencePrice(BigDecimal referencePrice) {
        this.referencePrice = referencePrice;
    }

    public TradeOrderStatus getStatus() {
        return status;
    }

    public void setStatus(TradeOrderStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public long getVersion() {
        return version;
    }
}
