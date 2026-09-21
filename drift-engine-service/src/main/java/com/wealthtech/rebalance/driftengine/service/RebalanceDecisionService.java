package com.wealthtech.rebalance.driftengine.service;

import com.wealthtech.rebalance.driftengine.config.RebalanceProperties;
import com.wealthtech.rebalance.driftengine.config.TaxRuleProperties;
import com.wealthtech.rebalance.grpc.account.*;
import com.wealthtech.rebalance.grpc.taxengine.GenerateTradesRequest;
import com.wealthtech.rebalance.grpc.taxengine.GenerateTradesResponse;
import com.wealthtech.rebalance.grpc.taxengine.TaxOptimizationServiceGrpc;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Makes the two outbound gRPC calls needed to decide one account's trades: read its position
 * snapshot from account-service, then hand that snapshot to tax-engine-service to get back the
 * final BUY/SELL list. Deliberately NOT {@code @Transactional} and does no database I/O of its
 * own -- a DB transaction should never sit open across a network call, so the persistence step
 * (see {@link RebalanceTransactionService}) happens in a separate call, after this one returns.
 */
@Service
public class RebalanceDecisionService {

    private final AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub;
    private final TaxOptimizationServiceGrpc.TaxOptimizationServiceBlockingStub taxOptimizationServiceStub;
    private final RebalanceProperties rebalanceProperties;
    private final TaxRuleProperties taxRuleProperties;

    public RebalanceDecisionService(AccountServiceGrpc.AccountServiceBlockingStub accountServiceStub,
                                     TaxOptimizationServiceGrpc.TaxOptimizationServiceBlockingStub taxOptimizationServiceStub,
                                     RebalanceProperties rebalanceProperties,
                                     TaxRuleProperties taxRuleProperties) {
        this.accountServiceStub = accountServiceStub;
        this.taxOptimizationServiceStub = taxOptimizationServiceStub;
        this.rebalanceProperties = rebalanceProperties;
        this.taxRuleProperties = taxRuleProperties;
    }

    public record Decision(AccountSnapshot snapshot, GenerateTradesResponse trades) {
    }

    public Decision decide(String accountId) {
        AccountSnapshot snapshot = accountServiceStub.getAccountSnapshot(
                GetAccountSnapshotRequest.newBuilder().setAccountId(accountId).build());

        Set<String> replacementSymbols = new LinkedHashSet<>();
        snapshot.getOpenLotsList().forEach(lot -> addIfPresent(replacementSymbols, lot.getWashSaleReplacementSymbol()));
        snapshot.getModelTargetsList().forEach(t -> addIfPresent(replacementSymbols, t.getWashSaleReplacementSymbol()));

        GenerateTradesRequest.Builder requestBuilder = GenerateTradesRequest.newBuilder()
                .setAccountId(accountId)
                .setTaxable(snapshot.getTaxable())
                .setTaxLossHarvestingEnabled(snapshot.getTaxLossHarvestingEnabled())
                .setCashBalance(snapshot.getCashBalance())
                .addAllOpenLots(snapshot.getOpenLotsList())
                .addAllModelTargets(snapshot.getModelTargetsList())
                .setDriftThresholdBps(rebalanceProperties.driftThresholdBps().toPlainString())
                .setMinTradeAmount(rebalanceProperties.minTradeAmount().toPlainString())
                .setCashBufferPct(rebalanceProperties.cashBufferPct().toPlainString())
                .setWashSaleWindowDays(taxRuleProperties.washSaleWindowDays())
                .setMinHarvestLoss(taxRuleProperties.minHarvestLoss().toPlainString())
                .setLotSelectionStrategy(taxRuleProperties.defaultLotSelectionStrategy())
                .setAsOfDate(LocalDate.now().toString());

        for (String symbol : replacementSymbols) {
            SecurityMsg replacement = accountServiceStub.getSecurityBySymbol(
                    GetSecurityBySymbolRequest.newBuilder().setSymbol(symbol).build());
            requestBuilder.addReplacementSecurities(replacement);
        }

        GenerateTradesResponse trades = taxOptimizationServiceStub.generateTrades(requestBuilder.build());
        return new Decision(snapshot, trades);
    }

    private static void addIfPresent(Set<String> set, String symbol) {
        if (symbol != null && !symbol.isBlank()) {
            set.add(symbol);
        }
    }
}
