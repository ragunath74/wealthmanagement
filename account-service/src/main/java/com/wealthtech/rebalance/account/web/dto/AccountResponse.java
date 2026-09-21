package com.wealthtech.rebalance.account.web.dto;

import com.wealthtech.rebalance.account.domain.ClientAccount;

import java.math.BigDecimal;

public record AccountResponse(
        String id,
        String advisorId,
        String modelPortfolioId,
        BigDecimal cashBalance,
        boolean taxable,
        boolean taxLossHarvestingEnabled
) {
    public static AccountResponse from(ClientAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getAdvisor().getId(),
                account.getModelPortfolio() == null ? null : account.getModelPortfolio().getId(),
                account.getCashBalance(),
                account.isTaxable(),
                account.isTaxLossHarvestingEnabled());
    }
}
