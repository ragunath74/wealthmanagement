package com.wealthtech.rebalance.account.config;

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
 * Boots a plain grpc-java {@link Server} as a Spring-managed lifecycle bean -- no third-party
 * Spring/gRPC starter, so exactly what's involved in running a gRPC server is visible here rather
 * than hidden behind auto-configuration. Every {@link BindableService} bean in the context (i.e.
 * every {@code *GrpcService} class below) is registered automatically. Reflection service is
 * enabled so {@code grpcurl -plaintext localhost:9091 list} works without shipping the .proto
 * file to whoever is testing the API.
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final int port;
    private final List<BindableService> services;
    private Server server;
    private volatile boolean running = false;

    public GrpcServerLifecycle(@Value("${grpc.server.port:9091}") int port, List<BindableService> services) {
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
        // start after the application context (and DB connection pool) is fully up
        return Integer.MAX_VALUE - 1;
    }
}
