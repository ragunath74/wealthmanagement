package com.wealthtech.rebalance.account.repository;

import com.wealthtech.rebalance.account.domain.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SecurityRepository extends JpaRepository<Security, String> {
    Optional<Security> findBySymbol(String symbol);
}
