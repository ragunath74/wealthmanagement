package com.wealthtech.rebalance.driftengine.service;

import com.wealthtech.rebalance.common.event.AccountRebalanceRequested;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.common.messaging.EventPublisher;
import com.wealthtech.rebalance.driftengine.config.RebalanceProperties;
import com.wealthtech.rebalance.grpc.account.AccountIdBatch;
import com.wealthtech.rebalance.grpc.account.AccountServiceGrpc;
import com.wealthtech.rebalance.grpc.account.ListAccountIdsForModelRequest;
import com.wealthtech.rebalance.grpc.account.ListAccountIdsHoldingSecurityRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.UUID;

/**
 * Fans a single trigger ("model portfolio changed" or "price moved significantly") out into one
 * {@code AccountRebalanceRequested} event per affected account, keyed by accountId so Kafka
 * partitioning keeps all activity for one account on one partition (ordering) while spreading the
 * millions of accounts across every partition (parallelism). Runs off the Kafka listener thread
 * via {@code @Async} so dispatching to 500,000 accounts never blocks the topic consumer.
 *
 * The keyset-paginated scan itself now happens inside account-service (see
 * ClientAccountRepository) and is streamed back here page-by-page over the gRPC server-streaming
 * RPCs -- this service just drains the stream and republishes each page as individual Kafka
 * events. Same algorithmic property as the monolith (constant-time pages regardless of scan
 * depth), different transport.
 */
@Service
public class RebalanceDispatchService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceDispatchService.class);

    private final AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub;
    private final EventPublisher eventPublisher;
    private final RebalanceProperties rebalanceProperties;

    public RebalanceDispatchService(AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub,
                                     EventPublisher eventPublisher,
                                     RebalanceProperties rebalanceProperties) {
        this.accountServiceStub = accountServiceStub;
        this.eventPublisher = eventPublisher;
        this.rebalanceProperties = rebalanceProperties;
    }

    @Async
    public void dispatchForModelUpdate(String modelPortfolioId, long revision) {
        ListAccountIdsForModelRequest request = ListAccountIdsForModelRequest.newBuilder()
                .setModelPortfolioId(modelPortfolioId)
                .setPageSize(rebalanceProperties.dispatchPageSize())
                .build();

        int total = 0;
        Iterator<AccountIdBatch> pages = accountServiceStub.listAccountIdsForModel(request);
        while (pages.hasNext()) {
            AccountIdBatch batch = pages.next();
            for (String accountId : batch.getAccountIdsList()) {
                publish(accountId, "MODEL_UPDATE", modelPortfolioId);
            }
            total += batch.getAccountIdsCount();
        }
        log.info("Dispatched rebalance requests for {} accounts on model {} revision {}", total, modelPortfolioId, revision);
    }

    @Async
    public void dispatchForPriceUpdate(String securityId, String symbol) {
        ListAccountIdsHoldingSecurityRequest request = ListAccountIdsHoldingSecurityRequest.newBuilder()
                .setSecurityId(securityId)
                .setPageSize(rebalanceProperties.dispatchPageSize())
                .build();

        int total = 0;
        Iterator<AccountIdBatch> pages = accountServiceStub.listAccountIdsHoldingSecurity(request);
        while (pages.hasNext()) {
            AccountIdBatch batch = pages.next();
            for (String accountId : batch.getAccountIdsList()) {
                publish(accountId, "PRICE_UPDATE", symbol);
            }
            total += batch.getAccountIdsCount();
        }
        log.info("Dispatched rebalance requests for {} accounts holding {} after price move", total, symbol);
    }

    private void publish(String accountId, String triggerType, String triggerRef) {
        AccountRebalanceRequested event = new AccountRebalanceRequested(UUID.randomUUID().toString(), accountId, triggerType, triggerRef);
        eventPublisher.publish(KafkaTopics.ACCOUNT_REBALANCE_REQUESTED, accountId, event);
    }
}
