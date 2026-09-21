package com.wealthtech.rebalance.account.web;

import com.wealthtech.rebalance.account.web.dto.PriceTickRequest;
import com.wealthtech.rebalance.common.event.PriceUpdateEvent;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.common.messaging.EventPublisher;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Stands in for a real market-data feed adapter, which would normally publish directly onto
 * {@code market.price.updates} from a FIX/websocket bridge. This endpoint lets the whole platform
 * be exercised end-to-end without one. Note this service does NOT update its own Security row
 * inline here -- it goes through Kafka like every other price update, so the demo exercises the
 * exact same code path a real feed would.
 */
@RestController
@RequestMapping("/api/prices")
public class PriceIngestController {

    private final EventPublisher eventPublisher;

    public PriceIngestController(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void publishTick(@Valid @RequestBody PriceTickRequest request) {
        PriceUpdateEvent event = new PriceUpdateEvent(UUID.randomUUID().toString(), request.symbol(), request.price(), Instant.now());
        eventPublisher.publish(KafkaTopics.MARKET_PRICE_UPDATES, request.symbol(), event);
    }
}
