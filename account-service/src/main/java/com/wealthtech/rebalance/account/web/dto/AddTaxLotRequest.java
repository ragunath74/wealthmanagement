package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Demo/seeding endpoint: directly opens a tax lot in an account, bypassing the trade lifecycle -- there's no brokerage-feed integration to seed starting positions from. */
public record AddTaxLotRequest(
        @NotBlank String symbol,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.0001") BigDecimal costBasisPerShare,
        @NotNull LocalDate acquiredDate
) {
}
