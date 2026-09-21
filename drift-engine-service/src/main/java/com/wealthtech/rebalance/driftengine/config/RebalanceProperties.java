package com.wealthtech.rebalance.driftengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "rebalance")
public record RebalanceProperties(
        BigDecimal driftThresholdBps,
        BigDecimal minTradeAmount,
        BigDecimal cashBufferPct,
        int dispatchPageSize,
        int lockWaitSeconds,
        BigDecimal priceMoveDispatchThresholdBps
) {
    public RebalanceProperties {
        if (driftThresholdBps == null) driftThresholdBps = BigDecimal.valueOf(500); // 5.00%
        if (minTradeAmount == null) minTradeAmount = BigDecimal.valueOf(25);
        if (cashBufferPct == null) cashBufferPct = BigDecimal.valueOf(0.005); // 0.5%
        if (dispatchPageSize <= 0) dispatchPageSize = 500;
        if (lockWaitSeconds <= 0) lockWaitSeconds = 5;
        // Gate price-driven fan-out on a minimum cumulative move so a busy ticker doesn't trigger
        // a full re-evaluation of every holding account on every single tick -- see ARCHITECTURE.md.
        if (priceMoveDispatchThresholdBps == null) priceMoveDispatchThresholdBps = BigDecimal.valueOf(100); // 1.00%
    }
}
