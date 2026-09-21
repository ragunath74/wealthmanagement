package com.wealthtech.rebalance.driftengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * drift-engine-service: the orchestrator. Reacts to Kafka triggers, calls account-service and
 * tax-engine-service over gRPC, and owns the trade decision with its own transactional outbox.
 * Uses the reusable outbox/idempotency/lock building blocks from the {@code common} module.
 */
@SpringBootApplication(scanBasePackages = {
        "com.wealthtech.rebalance.driftengine",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency",
        "com.wealthtech.rebalance.common.messaging",
        "com.wealthtech.rebalance.common.lock"
})
@EntityScan(basePackages = {
        "com.wealthtech.rebalance.driftengine.domain",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.wealthtech.rebalance.driftengine.repository",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class DriftEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriftEngineServiceApplication.class, args);
    }
}
