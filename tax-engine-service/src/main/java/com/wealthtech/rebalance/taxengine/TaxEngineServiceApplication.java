package com.wealthtech.rebalance.taxengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * tax-engine-service: stateless gRPC compute node. No database, no Kafka, no Redis are even on
 * this module's classpath (see pom.xml) -- there is nothing to exclude or misconfigure, which is
 * the whole point of keeping a compute service this lean.
 */
@SpringBootApplication
public class TaxEngineServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaxEngineServiceApplication.class, args);
    }
}
