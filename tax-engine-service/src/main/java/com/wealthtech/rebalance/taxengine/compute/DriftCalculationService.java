package com.wealthtech.rebalance.taxengine.compute;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure computation: given an account's open lots and its model portfolio's target weights,
 * produce the per-security drift. No I/O -- this is the same algorithm the original monolith
 * used, now living in tax-engine-service since that's where the reference architecture's
 * "Tax & Optimization" node owns final trade decisions.
 */
@Service
public class DriftCalculationService {

    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);
    private static final BigDecimal TEN_THOUSAND = BigDecimal.valueOf(10_000);

    public DriftReport calculateDrift(BigDecimal cashBalance, List<LotView> openLots, List<ModelTargetView> modelTargets) {
        Map<String, BigDecimal> quantityBySecurityId = new LinkedHashMap<>();
        Map<String, SecurityView> securityById = new LinkedHashMap<>();
        for (LotView lot : openLots) {
            SecurityView security = lot.security();
            securityById.put(security.id(), security);
            quantityBySecurityId.merge(security.id(), lot.quantity(), BigDecimal::add);
        }

        Map<String, BigDecimal> targetWeightBySecurityId = new LinkedHashMap<>();
        for (ModelTargetView target : modelTargets) {
            securityById.putIfAbsent(target.security().id(), target.security());
            targetWeightBySecurityId.put(target.security().id(), target.targetWeight());
        }

        BigDecimal investedValue = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : quantityBySecurityId.entrySet()) {
            SecurityView security = securityById.get(e.getKey());
            investedValue = investedValue.add(security.lastPrice().multiply(e.getValue()));
        }
        BigDecimal totalMarketValue = investedValue.add(cashBalance);

        List<SecurityDrift> perSecurity = securityById.values().stream()
                .map(security -> {
                    BigDecimal quantity = quantityBySecurityId.getOrDefault(security.id(), BigDecimal.ZERO);
                    BigDecimal targetWeight = targetWeightBySecurityId.getOrDefault(security.id(), BigDecimal.ZERO);
                    BigDecimal marketValue = security.lastPrice().multiply(quantity);
                    BigDecimal currentWeight = totalMarketValue.signum() == 0
                            ? BigDecimal.ZERO
                            : marketValue.divide(totalMarketValue, MC);
                    BigDecimal driftBps = currentWeight.subtract(targetWeight).multiply(TEN_THOUSAND, MC);
                    BigDecimal targetValue = targetWeight.multiply(totalMarketValue, MC);
                    BigDecimal dollarDelta = targetValue.subtract(marketValue);
                    return new SecurityDrift(security, quantity, currentWeight, targetWeight, driftBps, dollarDelta);
                })
                .toList();

        return new DriftReport(totalMarketValue, cashBalance, perSecurity);
    }
}
