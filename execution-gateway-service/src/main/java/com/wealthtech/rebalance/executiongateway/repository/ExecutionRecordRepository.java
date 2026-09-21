package com.wealthtech.rebalance.executiongateway.repository;

import com.wealthtech.rebalance.executiongateway.domain.ExecutionRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRecordRepository extends JpaRepository<ExecutionRecord, String> {
}
