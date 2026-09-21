package com.wealthtech.rebalance.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** A tradeable instrument. {@code lastPrice} is updated by {@code PriceUpdateListener} as market ticks arrive on {@code market.price.updates}. */
@Entity
@Table(name = "security", indexes = @Index(name = "idx_security_symbol", columnList = "symbol", unique = true))
public class Security {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false)
    private String name;

    /** Symbol of a correlated-but-not-"substantially identical" security, used to avoid wash sales while preserving exposure. */
    private String washSaleReplacementSymbol;

    @Column(nullable = false)
    private BigDecimal lastPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant lastPriceAt = Instant.EPOCH;

    @Version
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWashSaleReplacementSymbol() {
        return washSaleReplacementSymbol;
    }

    public void setWashSaleReplacementSymbol(String washSaleReplacementSymbol) {
        this.washSaleReplacementSymbol = washSaleReplacementSymbol;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public Instant getLastPriceAt() {
        return lastPriceAt;
    }

    public void setLastPriceAt(Instant lastPriceAt) {
        this.lastPriceAt = lastPriceAt;
    }

    public long getVersion() {
        return version;
    }
}
