package com.wealthtech.rebalance.driftengine.service;

import com.wealthtech.rebalance.driftengine.config.RebalanceProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Debounces the price-driven fan-out: re-evaluating every account holding a security on every
 * single tick doesn't survive contact with a liquid market (a security held in 200,000 accounts
 * ticking once a second would mean 200,000 rebalance evaluations per second). This gate keeps the
 * last symbol/price pair that actually triggered a dispatch in Redis (shared across all
 * drift-engine-service replicas) and only lets a new tick through once the cumulative move since
 * then exceeds {@code rebalance.price-move-dispatch-threshold-bps}.
 */
@Service
public class PriceMoveGateService {

    private static final String KEY_PREFIX = "drift-engine:last-dispatched-price:";
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

    private final StringRedisTemplate redisTemplate;
    private final RebalanceProperties rebalanceProperties;

    public PriceMoveGateService(StringRedisTemplate redisTemplate, RebalanceProperties rebalanceProperties) {
        this.redisTemplate = redisTemplate;
        this.rebalanceProperties = rebalanceProperties;
    }

    /** @return true if this tick's move is significant enough to warrant a dispatch (and records it as the new baseline). */
    public boolean isSignificantMove(String symbol, BigDecimal newPrice) {
        String key = KEY_PREFIX + symbol;
        String lastStr = redisTemplate.opsForValue().get(key);
        boolean significant;
        if (lastStr == null) {
            significant = true; // first tick we've ever seen for this symbol
        } else {
            BigDecimal lastPrice = new BigDecimal(lastStr);
            significant = lastPrice.signum() == 0
                    || lastPrice.subtract(newPrice).abs()
                            .divide(lastPrice, 8, RoundingMode.HALF_UP)
                            .multiply(TEN_THOUSAND)
                            .compareTo(rebalanceProperties.priceMoveDispatchThresholdBps()) >= 0;
        }
        if (significant) {
            redisTemplate.opsForValue().set(key, newPrice.toPlainString());
        }
        return significant;
    }
}
