package com.wealthtech.rebalance.executiongateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * execution-gateway-service: consumes trade orders, simulates the FIX-routed fill, republishes
 * settlement. Deliberately Kafka-only -- no gRPC client or server here, unlike drift-engine and
 * account-service, because nothing needs a synchronous request/response with this service; the
 * order hand-off and the settlement hand-back are both fire-and-forget events (see
 * ARCHITECTURE.md "gRPC vs Kafka: which calls are which, and why").
 */
@SpringBootApplication(scanBasePackages = {
        "com.wealthtech.rebalance.executiongateway",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency",
        "com.wealthtech.rebalance.common.messaging"
})
@EntityScan(basePackages = {
        "com.wealthtech.rebalance.executiongateway.domain",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.wealthtech.rebalance.executiongateway.repository",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableScheduling
public class ExecutionGatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutionGatewayServiceApplication.class, args);
    }
}
