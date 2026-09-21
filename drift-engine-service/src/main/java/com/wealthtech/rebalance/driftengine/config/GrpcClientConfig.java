package com.wealthtech.rebalance.driftengine.config;

import com.wealthtech.rebalance.grpc.account.AccountServiceGrpc;
import com.wealthtech.rebalance.grpc.taxengine.TaxOptimizationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Plain grpc-java channels, built the same explicit way the server side is (see
 * account-service's / tax-engine-service's {@code GrpcServerLifecycle}) -- no client-side
 * Spring/gRPC starter magic. In a real deployment these hosts would resolve through Kubernetes
 * DNS / a service mesh rather than static config; usePlaintext() is a local-dev/demo shortcut
 * (production would use TLS -- see ARCHITECTURE.md "What's simplified").
 */
@Configuration
public class GrpcClientConfig {

    private ManagedChannel accountChannel;
    private ManagedChannel taxEngineChannel;

    @Bean
    public ManagedChannel accountServiceChannel(@Value("${grpc.client.account-service.host:localhost}") String host,
                                                 @Value("${grpc.client.account-service.port:9091}") int port) {
        accountChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        return accountChannel;
    }

    @Bean
    public ManagedChannel taxEngineServiceChannel(@Value("${grpc.client.tax-engine-service.host:localhost}") String host,
                                                   @Value("${grpc.client.tax-engine-service.port:9092}") int port) {
        taxEngineChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        return taxEngineChannel;
    }

    @Bean
    public AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub(ManagedChannel accountServiceChannel) {
        return AccountServiceGrpc.newBlockingStub(accountServiceChannel);
    }

    @Bean
    public TaxOptimizationServiceGrpc.TaxOptimizationServiceBlockingStub taxOptimizationServiceStub(ManagedChannel taxEngineServiceChannel) {
        return TaxOptimizationServiceGrpc.newBlockingStub(taxEngineServiceChannel);
    }

    @PreDestroy
    public void shutdown() {
        if (accountChannel != null) accountChannel.shutdown();
        if (taxEngineChannel != null) taxEngineChannel.shutdown();
        try {
            if (accountChannel != null) accountChannel.awaitTermination(5, TimeUnit.SECONDS);
            if (taxEngineChannel != null) taxEngineChannel.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
