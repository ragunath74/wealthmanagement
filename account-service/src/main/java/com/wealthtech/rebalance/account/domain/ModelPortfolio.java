package com.wealthtech.rebalance.account.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "model_portfolio")
public class ModelPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** Monotonically increasing revision, bumped on every target-weight change and published on model.portfolio.updates. */
    @Column(nullable = false)
    private long revision = 0;

    @OneToMany(mappedBy = "modelPortfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModelPortfolioTarget> targets = new ArrayList<>();

    @Version
    private long version;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getRevision() {
        return revision;
    }

    public void setRevision(long revision) {
        this.revision = revision;
    }

    public List<ModelPortfolioTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<ModelPortfolioTarget> targets) {
        this.targets = targets;
    }

    public long getVersion() {
        return version;
    }
}
