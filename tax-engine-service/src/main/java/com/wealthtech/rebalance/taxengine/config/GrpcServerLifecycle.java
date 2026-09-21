package com.wealthtech.rebalance.taxengine.config;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionServiceV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Boots a plain grpc-java {@link Server} as a Spring-managed lifecycle bean. Deliberately
 * duplicated (rather than shared via the {@code common} module) between account-service and
 * tax-engine-service: it's ~60 lines of infra glue, and sharing it would force this
 * intentionally dependency-free service to pull in common's Kafka/JPA/Redis dependencies for
 * something it doesn't use. Not every piece of near-identical code belongs in a shared library --
 * see ARCHITECTURE.md "What's shared vs. duplicated, and why".
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final int port;
    private final List<BindableService> services;
    private Server server;
    private volatile boolean running = false;

    public GrpcServerLifecycle(@Value("${grpc.server.port:9092}") int port, List<BindableService> services) {
        this.port = port;
        this.services = services;
    }

    @Override
    public void start() {
        try {
            ServerBuilder<?> builder = ServerBuilder.forPort(port);
            services.forEach(builder::addService);
            builder.addService(ProtoReflectionServiceV1.newInstance());
            server = builder.build().start();
            running = true;
            log.info("gRPC server started on port {} with services {}", port,
                    services.stream().map(s -> s.getClass().getSimpleName()).toList());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start gRPC server on port " + port, e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE - 1;
    }
}
