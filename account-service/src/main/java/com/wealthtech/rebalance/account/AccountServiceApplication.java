package com.wealthtech.rebalance.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * account-service: system of record for advisors, securities, model portfolios, client accounts
 * and tax lots. Only reachable by other services via its gRPC API ({@code AccountGrpcService}) --
 * never through a shared database. Pulls in the reusable outbox/idempotency building blocks from
 * the {@code common} module via explicit basePackage scanning, since they live outside this
 * service's own package tree.
 */
@SpringBootApplication(scanBasePackages = {
        "com.wealthtech.rebalance.account",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency",
        "com.wealthtech.rebalance.common.messaging"
})
@EntityScan(basePackages = {
        "com.wealthtech.rebalance.account.domain",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.wealthtech.rebalance.account.repository",
        "com.wealthtech.rebalance.common.outbox",
        "com.wealthtech.rebalance.common.idempotency"
})
@EnableScheduling
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }


}
