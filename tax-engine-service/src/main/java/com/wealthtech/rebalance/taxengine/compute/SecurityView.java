package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;

/**
 * A read-only snapshot of a security, as sent in by drift-engine-service (which sourced it from
 * account-service over gRPC). tax-engine-service never reads a Security row itself -- it has no
 * database -- this is the whole point of a stateless compute service: everything it needs arrives
 * on the request.
 */
public record SecurityView(
        String id,
        String symbol,
        BigDecimal lastPrice,
        String washSaleReplacementSymbol
) {
}
