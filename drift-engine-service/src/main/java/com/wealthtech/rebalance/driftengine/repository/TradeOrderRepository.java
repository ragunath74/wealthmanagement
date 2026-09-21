package com.wealthtech.rebalance.driftengine.repository;

import com.wealthtech.rebalance.driftengine.domain.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder, String> {
    List<TradeOrder> findByAccountIdOrderByCreatedAtDesc(String accountId);
    Optional<TradeOrder> findByIdempotencyKey(String idempotencyKey);
}
