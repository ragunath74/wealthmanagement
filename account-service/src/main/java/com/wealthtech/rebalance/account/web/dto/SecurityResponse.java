package com.wealthtech.rebalance.account.web.dto;

import com.wealthtech.rebalance.account.domain.Security;

import java.math.BigDecimal;

public record SecurityResponse(String id, String symbol, String name, BigDecimal lastPrice) {
    public static SecurityResponse from(Security security) {
        return new SecurityResponse(security.getId(), security.getSymbol(), security.getName(), security.getLastPrice());
    }
}
