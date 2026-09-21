package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Demo/simulation endpoint standing in for a real market-data feed adapter. */
public record PriceTickRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal price
) {
}
