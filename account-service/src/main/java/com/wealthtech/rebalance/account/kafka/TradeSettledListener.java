package com.wealthtech.rebalance.account.kafka;

import com.wealthtech.rebalance.account.domain.ClientAccount;
import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.domain.TaxLot;
import com.wealthtech.rebalance.account.repository.ClientAccountRepository;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.account.repository.TaxLotRepository;
import com.wealthtech.rebalance.common.event.LotAllocationPayload;
import com.wealthtech.rebalance.common.event.TradeSettledPayload;
import com.wealthtech.rebalance.common.idempotency.IdempotencyService;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * This is the completing step of the trade saga: drift-engine-service DECIDED the trade and
 * execution-gateway-service FILLED it, but neither of them is allowed to touch account-service's
 * tables directly (no shared database across services). So the position/cash mutation happens
 * here, asynchronously, triggered by the same {@code trade.settled} event that
 * drift-engine-service also consumes (in its own consumer group) to mark the order FILLED.
 * Between "trade decided" and "this listener runs" the account's cached position is briefly
 * stale relative to the in-flight trade -- an explicit, documented eventual-consistency window
 * (see ARCHITECTURE.md "Saga and eventual consistency").
 */
@Component
public class TradeSettledListener {

    private static final Logger log = LoggerFactory.getLogger(TradeSettledListener.class);
    private static final String CONSUMER_NAME = "account-service-trade-settled-consumer";

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final ClientAccountRepository clientAccountRepository;
    private final TaxLotRepository taxLotRepository;
    private final SecurityRepository securityRepository;

    public TradeSettledListener(ObjectMapper objectMapper,
                                 IdempotencyService idempotencyService,
                                 ClientAccountRepository clientAccountRepository,
                                 TaxLotRepository taxLotRepository,
                                 SecurityRepository securityRepository) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.clientAccountRepository = clientAccountRepository;
        this.taxLotRepository = taxLotRepository;
        this.securityRepository = securityRepository;
    }

    @KafkaListener(topics = KafkaTopics.TRADE_SETTLED, groupId = CONSUMER_NAME)
    @Transactional
    public void onMessage(String json) {
        TradeSettledPayload event = objectMapper.readValue(json, TradeSettledPayload.class);
        if (!idempotencyService.tryClaim(event.idempotencyKey(), CONSUMER_NAME)) {
            log.debug("Duplicate settlement for order {} ignored", event.orderId());
            return;
        }

        ClientAccount account = clientAccountRepository.findById(event.accountId())
                .orElseThrow(() -> new EntityNotFoundException("ClientAccount " + event.accountId()));
        BigDecimal notional = event.quantity().multiply(event.fillPrice());

        if ("BUY".equals(event.side())) {
            Security security = securityRepository.findById(event.securityId())
                    .orElseThrow(() -> new EntityNotFoundException("Security " + event.securityId()));
            TaxLot lot = new TaxLot();
            lot.setAccount(account);
            lot.setSecurity(security);
            lot.setQuantity(event.quantity());
            lot.setCostBasisPerShare(event.fillPrice());
            lot.setAcquiredDate(LocalDate.ofInstant(event.filledAt() == null ? Instant.now() : event.filledAt(), ZoneOffset.UTC));
            taxLotRepository.save(lot);
            account.setCashBalance(account.getCashBalance().subtract(notional));
        } else {
            for (LotAllocationPayload allocation : event.lotAllocations()) {
                TaxLot lot = taxLotRepository.findByIdAndAccountId(allocation.lotId(), account.getId())
                        .orElseThrow(() -> new EntityNotFoundException("TaxLot " + allocation.lotId()));
                BigDecimal remaining = lot.getQuantity().subtract(allocation.quantity());
                lot.setQuantity(remaining);
                if (remaining.signum() <= 0) {
                    lot.setClosed(true);
                    lot.setClosedAt(Instant.now());
                }
                taxLotRepository.save(lot);
            }
            account.setCashBalance(account.getCashBalance().add(notional));
        }
        clientAccountRepository.save(account);
        log.info("Applied settlement for order {} ({} {} {})", event.orderId(), event.side(), event.quantity(), event.symbol());
    }
}
