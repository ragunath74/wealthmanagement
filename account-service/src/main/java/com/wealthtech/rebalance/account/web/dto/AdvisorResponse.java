package com.wealthtech.rebalance.account.web.dto;

import com.wealthtech.rebalance.account.domain.Advisor;

public record AdvisorResponse(String id, String displayName, String email) {
    public static AdvisorResponse from(Advisor advisor) {
        return new AdvisorResponse(advisor.getId(), advisor.getDisplayName(), advisor.getEmail());
    }
}
