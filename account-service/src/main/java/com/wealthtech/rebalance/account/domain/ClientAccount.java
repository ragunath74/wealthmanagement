package com.wealthtech.rebalance.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "client_account", indexes = {
        @Index(name = "idx_account_model", columnList = "model_portfolio_id"),
        @Index(name = "idx_account_advisor", columnList = "advisor_id")
})
public class ClientAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advisor_id", nullable = false)
    private Advisor advisor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_portfolio_id")
    private ModelPortfolio modelPortfolio;

    @Column(nullable = false)
    private BigDecimal cashBalance = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean taxLossHarvestingEnabled = true;

    /** True for retirement/tax-advantaged wrappers (IRA, 401k) where wash-sale/tax-lot optimization is irrelevant. */
    @Column(nullable = false)
    private boolean taxable = true;

    /**
     * Optimistic lock. Mutated both by settlement events (from trade.settled) and, in principle,
     * by concurrent admin edits -- a version conflict here fails fast with
     * ObjectOptimisticLockingFailureException instead of silently losing a cash/position update.
     */
    @Version
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Advisor getAdvisor() {
        return advisor;
    }

    public void setAdvisor(Advisor advisor) {
        this.advisor = advisor;
    }

    public ModelPortfolio getModelPortfolio() {
        return modelPortfolio;
    }

    public void setModelPortfolio(ModelPortfolio modelPortfolio) {
        this.modelPortfolio = modelPortfolio;
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public void setCashBalance(BigDecimal cashBalance) {
        this.cashBalance = cashBalance;
    }

    public boolean isTaxLossHarvestingEnabled() {
        return taxLossHarvestingEnabled;
    }

    public void setTaxLossHarvestingEnabled(boolean taxLossHarvestingEnabled) {
        this.taxLossHarvestingEnabled = taxLossHarvestingEnabled;
    }

    public boolean isTaxable() {
        return taxable;
    }

    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
    }

    public long getVersion() {
        return version;
    }
}
