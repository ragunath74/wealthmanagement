package com.wealthtech.rebalance.account.web.dto;

import com.wealthtech.rebalance.account.domain.ModelPortfolio;

import java.util.Map;
import java.util.stream.Collectors;

public record ModelPortfolioResponse(
        String id,
        String name,
        long revision,
        Map<String, String> targetWeightsBySymbol
) {
    public static ModelPortfolioResponse from(ModelPortfolio model) {
        return new ModelPortfolioResponse(
                model.getId(),
                model.getName(),
                model.getRevision(),
                model.getTargets().stream().collect(Collectors.toMap(
                        t -> t.getSecurity().getSymbol(),
                        t -> t.getTargetWeight().toPlainString())));
    }
}
