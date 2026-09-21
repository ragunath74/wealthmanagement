package com.wealthtech.rebalance.driftengine.web;

import com.wealthtech.rebalance.driftengine.repository.TradeOrderRepository;
import com.wealthtech.rebalance.driftengine.web.dto.TradeOrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class TradeOrderController {

    private final TradeOrderRepository tradeOrderRepository;

    public TradeOrderController(TradeOrderRepository tradeOrderRepository) {
        this.tradeOrderRepository = tradeOrderRepository;
    }

    /** The audit trail of every trade decision drift-engine-service has made for this account, including status as it's updated by {@code trade.settled}. */
    @GetMapping("/{id}/trade-orders")
    public List<TradeOrderResponse> tradeOrders(@PathVariable String id) {
        return tradeOrderRepository.findByAccountIdOrderByCreatedAtDesc(id).stream().map(TradeOrderResponse::from).toList();
    }
}
