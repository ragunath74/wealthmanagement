package com.wealthtech.rebalance.driftengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "tax-rules")
public record TaxRuleProperties(
        int washSaleWindowDays,
        String defaultLotSelectionStrategy,
        BigDecimal minHarvestLoss
) {
    public TaxRuleProperties {
        if (washSaleWindowDays <= 0) washSaleWindowDays = 30;
        if (defaultLotSelectionStrategy == null) defaultLotSelectionStrategy = "TAX_OPTIMAL";
        if (minHarvestLoss == null) minHarvestLoss = BigDecimal.valueOf(50);
    }
}
