package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateSecurityRequest(
        @NotBlank String symbol,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal initialPrice,
        String washSaleReplacementSymbol
) {
}
