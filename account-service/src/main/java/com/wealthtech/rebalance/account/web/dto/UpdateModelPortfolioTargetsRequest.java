package com.wealthtech.rebalance.account.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateModelPortfolioTargetsRequest(
        @NotEmpty @Valid List<TargetWeightRequest> targets
) {
}
