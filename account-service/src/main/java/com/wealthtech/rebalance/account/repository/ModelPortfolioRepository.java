package com.wealthtech.rebalance.account.repository;

import com.wealthtech.rebalance.account.domain.ModelPortfolio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModelPortfolioRepository extends JpaRepository<ModelPortfolio, String> {

    @EntityGraph(attributePaths = {"targets", "targets.security"})
    Optional<ModelPortfolio> findWithTargetsById(String id);
}
