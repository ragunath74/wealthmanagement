package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TargetWeightRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal targetWeight
) {
}
