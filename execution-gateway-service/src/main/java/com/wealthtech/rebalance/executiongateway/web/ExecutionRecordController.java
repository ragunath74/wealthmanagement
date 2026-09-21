package com.wealthtech.rebalance.executiongateway.web;

import com.wealthtech.rebalance.executiongateway.domain.ExecutionRecord;
import com.wealthtech.rebalance.executiongateway.repository.ExecutionRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ExecutionRecordController {

    private final ExecutionRecordRepository executionRecordRepository;

    public ExecutionRecordController(ExecutionRecordRepository executionRecordRepository) {
        this.executionRecordRepository = executionRecordRepository;
    }

    @GetMapping("/api/executions")
    public List<ExecutionRecord> list() {
        return executionRecordRepository.findAll();
    }
}
