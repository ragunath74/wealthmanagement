package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String advisorId,
        String modelPortfolioId,
        @NotNull BigDecimal initialCashBalance,
        boolean taxable,
        boolean taxLossHarvestingEnabled
) {
}
