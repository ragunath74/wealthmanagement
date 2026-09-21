package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAdvisorRequest(
        @NotBlank String displayName,
        @NotBlank @Email String email
) {
}
