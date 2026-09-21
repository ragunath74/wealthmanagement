package com.wealthtech.rebalance.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single purchase lot of a security held in an account. Positions are always derived by
 * summing open lots rather than tracked as a separate mutable balance, so tax accounting
 * (FIFO/LIFO/HIFO, holding period, wash sale) is always exact. Mutated ONLY by
 * {@code TradeSettledListener}, asynchronously, in response to {@code trade.settled} -- never
 * synchronously by the trade-decision path in drift-engine-service.
 */
@Entity
@Table(name = "tax_lot", indexes = {
        @Index(name = "idx_lot_account_security", columnList = "account_id, security_id"),
        @Index(name = "idx_lot_open", columnList = "account_id, security_id, closed")
})
public class TaxLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private ClientAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false)
    private LocalDate acquiredDate;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(nullable = false)
    private BigDecimal costBasisPerShare;

    @Column(nullable = false)
    private boolean closed = false;

    private Instant closedAt;

    @Version
    private long version;

    public boolean isLongTerm(LocalDate asOf) {
        return acquiredDate.plusYears(1).isBefore(asOf) || acquiredDate.plusYears(1).isEqual(asOf);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ClientAccount getAccount() {
        return account;
    }

    public void setAccount(ClientAccount account) {
        this.account = account;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public LocalDate getAcquiredDate() {
        return acquiredDate;
    }

    public void setAcquiredDate(LocalDate acquiredDate) {
        this.acquiredDate = acquiredDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getCostBasisPerShare() {
        return costBasisPerShare;
    }

    public void setCostBasisPerShare(BigDecimal costBasisPerShare) {
        this.costBasisPerShare = costBasisPerShare;
    }

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public long getVersion() {
        return version;
    }
}
