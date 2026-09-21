package com.wealthtech.rebalance.driftengine.kafka;

import com.wealthtech.rebalance.common.event.PriceUpdateEvent;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.driftengine.service.PriceMoveGateService;
import com.wealthtech.rebalance.driftengine.service.RebalanceDispatchService;
import com.wealthtech.rebalance.grpc.account.AccountServiceGrpc;
import com.wealthtech.rebalance.grpc.account.GetSecurityBySymbolRequest;
import com.wealthtech.rebalance.grpc.account.SecurityMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes {@code market.price.updates} in its OWN consumer group so it gets a full independent
 * copy of the stream -- account-service subscribes to the same topic under a different group id
 * purely to update its own Security.lastPrice, which is the classic Kafka pub/sub fan-out (one
 * topic, N independent consumer groups, each sees every message).
 */
@Component
public class PriceUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(PriceUpdateListener.class);
    private static final String CONSUMER_NAME = "drift-engine-price-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final PriceMoveGateService priceMoveGateService;
    private final RebalanceDispatchService rebalanceDispatchService;
    private final AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub;

    public PriceUpdateListener(ObjectMapper objectMapper,
                                IdempotencyService idempotencyService,
                                PriceMoveGateService priceMoveGateService,
                                RebalanceDispatchService rebalanceDispatchService,
                                AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.priceMoveGateService = priceMoveGateService;
        this.rebalanceDispatchService = rebalanceDispatchService;
        this.accountServiceStub = accountServiceStub;
    }

    @KafkaListener(topics = KafkaTopics.MARKET_PRICE_UPDATES, groupId = CONSUMER_NAME)
    public void onMessage(String json) {
        PriceUpdateEvent event = objectMapper.readValue(json, PriceUpdateEvent.class);
        if (!idempotencyService.tryClaim(event.eventId(), CONSUMER_NAME)) {
            log.debug("Duplicate price update event {} ignored", event.eventId());
            return;
        }
        if (!priceMoveGateService.isSignificantMove(event.symbol(), event.price())) {
            log.debug("Price move for {} below dispatch threshold, skipping fan-out", event.symbol());
            return;
        }
        SecurityMsg security = accountServiceStub.getSecurityBySymbol(
                GetSecurityBySymbolRequest.newBuilder().setSymbol(event.symbol()).build());
        rebalanceDispatchService.dispatchForPriceUpdate(security.getSecurityId(), event.symbol());
    }
}
