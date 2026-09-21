package com.wealthtech.rebalance.account.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "model_portfolio_target",
        uniqueConstraints = @UniqueConstraint(columnNames = {"model_portfolio_id", "security_id"}))
public class ModelPortfolioTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_portfolio_id", nullable = false)
    private ModelPortfolio modelPortfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal targetWeight;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ModelPortfolio getModelPortfolio() {
        return modelPortfolio;
    }

    public void setModelPortfolio(ModelPortfolio modelPortfolio) {
        this.modelPortfolio = modelPortfolio;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public BigDecimal getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(BigDecimal targetWeight) {
        this.targetWeight = targetWeight;
    }
}
