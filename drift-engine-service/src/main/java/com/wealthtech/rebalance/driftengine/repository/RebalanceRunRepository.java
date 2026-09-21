package com.wealthtech.rebalance.driftengine.repository;

import com.wealthtech.rebalance.driftengine.domain.RebalanceRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebalanceRunRepository extends JpaRepository<RebalanceRun, String> {
}
