package com.wealthtech.rebalance.account.repository;

import com.wealthtech.rebalance.account.domain.Advisor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvisorRepository extends JpaRepository<Advisor, String> {
}
