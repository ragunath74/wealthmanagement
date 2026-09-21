package com.wealthtech.rebalance.driftengine.service;

import com.wealthtech.rebalance.common.event.LotAllocationPayload;
import com.wealthtech.rebalance.common.event.TradeOrderPublishedPayload;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.common.outbox.OutboxEvent;
import com.wealthtech.rebalance.common.outbox.OutboxEventRepository;
import com.wealthtech.rebalance.driftengine.domain.RebalanceRun;
import com.wealthtech.rebalance.driftengine.domain.TradeOrder;
import com.wealthtech.rebalance.driftengine.domain.TradeOrderStatus;
import com.wealthtech.rebalance.driftengine.repository.RebalanceRunRepository;
import com.wealthtech.rebalance.driftengine.repository.TradeOrderRepository;
import com.wealthtech.rebalance.grpc.taxengine.GeneratedTradeMsg;
import com.wealthtech.rebalance.grpc.taxengine.GenerateTradesResponse;
import com.wealthtech.rebalance.grpc.taxengine.LotAllocationMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The single write-transaction that persists a trade decision: TradeOrder rows and the outbox
 * event(s) that will carry them to execution-gateway-service commit together, so there is no
 * window where a trade is decided but never queued for execution, nor one where an outbox row
 * exists without a corresponding order.
 */
@Service
public class RebalanceTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceTransactionService.class);

    private final TradeOrderRepository tradeOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RebalanceRunRepository rebalanceRunRepository;
    private final ObjectMapper objectMapper;

    public RebalanceTransactionService(TradeOrderRepository tradeOrderRepository,
                                        OutboxEventRepository outboxEventRepository,
                                        RebalanceRunRepository rebalanceRunRepository,
                                        ObjectMapper objectMapper) {
        this.tradeOrderRepository = tradeOrderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.rebalanceRunRepository = rebalanceRunRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int persistDecision(String accountId, String triggerType, String triggerEventId, GenerateTradesResponse decision) {
        RebalanceRun run = new RebalanceRun();
        run.setAccountId(accountId);
        run.setTriggerType(triggerType);

        List<GeneratedTradeMsg> trades = decision.getTradesList();
        if (trades.isEmpty()) {
            run.setSkipped(true);
            run.setSkipReason("NO_DRIFT_OR_HARVEST_CANDIDATE");
            run.setCompletedAt(Instant.now());
            rebalanceRunRepository.save(run);
            return 0;
        }

        int persisted = 0;
        for (GeneratedTradeMsg trade : trades) {
            String idempotencyKey = triggerEventId + ":" + trade.getSecurityId() + ":" + trade.getSide();
            if (tradeOrderRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
                log.warn("Duplicate trade suppressed for idempotencyKey={}", idempotencyKey);
                continue;
            }

            TradeOrder order = new TradeOrder();
            order.setAccountId(accountId);
            order.setSecurityId(trade.getSecurityId());
            order.setSymbol(trade.getSymbol());
            order.setSide(trade.getSide());
            order.setQuantity(new BigDecimal(trade.getQuantity()));
            order.setReferencePrice(new BigDecimal(trade.getReferencePrice()));
            order.setReason(trade.getReason());
            order.setStatus(TradeOrderStatus.GENERATED);
            order.setIdempotencyKey(idempotencyKey);
            order = tradeOrderRepository.save(order);

            List<LotAllocationPayload> lotAllocations = trade.getLotAllocationsList().stream()
                    .map(this::toLotAllocationPayload)
                    .toList();

            TradeOrderPublishedPayload payload = new TradeOrderPublishedPayload(
                    order.getId(), accountId, trade.getSecurityId(), trade.getSymbol(), trade.getSide(),
                    order.getQuantity(), order.getReferencePrice(), trade.getReason(), idempotencyKey,
                    order.getCreatedAt(), lotAllocations);

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTopic(KafkaTopics.TRADE_ORDERS_OUTBOUND);
            outboxEvent.setAggregateId(accountId);
            outboxEvent.setEventType("TRADE_ORDER_GENERATED");
            outboxEvent.setPayload(objectMapper.writeValueAsString(payload));
            outboxEventRepository.save(outboxEvent);
            persisted++;
        }

        run.setTradesGenerated(persisted);
        run.setCompletedAt(Instant.now());
        rebalanceRunRepository.save(run);
        return persisted;
    }

    /** Called by {@code TradeSettledListener} once execution-gateway-service confirms a fill. */
    @Transactional
    public void markFilled(String idempotencyKey) {
        tradeOrderRepository.findByIdempotencyKey(idempotencyKey).ifPresent(order -> {
            order.setStatus(TradeOrderStatus.FILLED);
            tradeOrderRepository.save(order);
        });
    }

    private LotAllocationPayload toLotAllocationPayload(LotAllocationMsg msg) {
        return new LotAllocationPayload(msg.getLotId(), new BigDecimal(msg.getQuantity()), new BigDecimal(msg.getRealizedGainLoss()));
    }
}
