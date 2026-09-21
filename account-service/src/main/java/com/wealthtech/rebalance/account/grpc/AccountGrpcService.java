package com.wealthtech.rebalance.account.grpc;

import com.wealthtech.rebalance.account.domain.ClientAccount;
import com.wealthtech.rebalance.account.domain.ModelPortfolio;
import com.wealthtech.rebalance.account.domain.ModelPortfolioTarget;
import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.domain.TaxLot;
import com.wealthtech.rebalance.account.repository.ClientAccountRepository;
import com.wealthtech.rebalance.account.repository.ModelPortfolioRepository;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.account.repository.TaxLotRepository;
import com.wealthtech.rebalance.grpc.account.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * gRPC server implementation for account-service. This is the ONLY door into account-service's
 * position/reference data for other services -- drift-engine-service never touches this
 * database. Streaming methods do the SAME keyset-pagination repository calls the original
 * monolith's dispatch service did directly; the only thing that changed is the transport
 * (in-process method call -> gRPC server-streaming RPC).
 */
@Service
public class AccountGrpcService extends AccountServiceGrpc.AccountServiceImplBase {

    private static final int STREAM_PAGE_SIZE = 500;

    private final ClientAccountRepository clientAccountRepository;
    private final ModelPortfolioRepository modelPortfolioRepository;
    private final TaxLotRepository taxLotRepository;
    private final SecurityRepository securityRepository;

    public AccountGrpcService(ClientAccountRepository clientAccountRepository,
                               ModelPortfolioRepository modelPortfolioRepository,
                               TaxLotRepository taxLotRepository,
                               SecurityRepository securityRepository) {
        this.clientAccountRepository = clientAccountRepository;
        this.modelPortfolioRepository = modelPortfolioRepository;
        this.taxLotRepository = taxLotRepository;
        this.securityRepository = securityRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void getAccountSnapshot(GetAccountSnapshotRequest request, StreamObserver<AccountSnapshot> responseObserver) {
        Optional<ClientAccount> accountOpt = clientAccountRepository.findById(request.getAccountId());
        if (accountOpt.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("ClientAccount " + request.getAccountId())
                    .asRuntimeException());
            return;
        }
        ClientAccount account = accountOpt.get();
        List<TaxLot> openLots = taxLotRepository.findByAccountIdAndClosedFalse(account.getId());

        AccountSnapshot.Builder builder = AccountSnapshot.newBuilder()
                .setAccountId(account.getId())
                .setAdvisorId(account.getAdvisor().getId())
                .setCashBalance(account.getCashBalance().toPlainString())
                .setTaxable(account.isTaxable())
                .setTaxLossHarvestingEnabled(account.isTaxLossHarvestingEnabled());

        for (TaxLot lot : openLots) {
            Security security = lot.getSecurity();
            builder.addOpenLots(TaxLotMsg.newBuilder()
                    .setLotId(lot.getId())
                    .setSecurityId(security.getId())
                    .setSymbol(security.getSymbol())
                    .setQuantity(lot.getQuantity().toPlainString())
                    .setCostBasisPerShare(lot.getCostBasisPerShare().toPlainString())
                    .setAcquiredDate(lot.getAcquiredDate().toString())
                    .setLastPrice(security.getLastPrice().toPlainString())
                    .setWashSaleReplacementSymbol(nullToEmpty(security.getWashSaleReplacementSymbol()))
                    .build());
        }

        if (account.getModelPortfolio() != null) {
            ModelPortfolio model = modelPortfolioRepository.findWithTargetsById(account.getModelPortfolio().getId())
                    .orElse(null);
            if (model != null) {
                builder.setModelPortfolioId(model.getId()).setModelRevision(model.getRevision());
                for (ModelPortfolioTarget target : model.getTargets()) {
                    Security security = target.getSecurity();
                    builder.addModelTargets(ModelTargetMsg.newBuilder()
                            .setSecurityId(security.getId())
                            .setSymbol(security.getSymbol())
                            .setTargetWeight(target.getTargetWeight().toPlainString())
                            .setLastPrice(security.getLastPrice().toPlainString())
                            .setWashSaleReplacementSymbol(nullToEmpty(security.getWashSaleReplacementSymbol()))
                            .build());
                }
            }
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void listAccountIdsForModel(ListAccountIdsForModelRequest request, StreamObserver<AccountIdBatch> responseObserver) {
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : STREAM_PAGE_SIZE;
        String lastSeenId = null;

        while (true) {
            List<ClientAccount> page = (lastSeenId == null)
                    ? clientAccountRepository.findByModelPortfolioIdOrderByIdAsc(request.getModelPortfolioId(), PageRequest.of(0, pageSize))
                    : clientAccountRepository.findByModelPortfolioIdAndIdGreaterThanOrderByIdAsc(request.getModelPortfolioId(), lastSeenId, PageRequest.of(0, pageSize));

            boolean isLast = page.size() < pageSize;
            AccountIdBatch.Builder batch = AccountIdBatch.newBuilder().setIsLast(isLast);
            page.forEach(a -> batch.addAccountIds(a.getId()));
            responseObserver.onNext(batch.build());

            if (page.isEmpty() || isLast) {
                break;
            }
            lastSeenId = page.get(page.size() - 1).getId();
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void listAccountIdsHoldingSecurity(ListAccountIdsHoldingSecurityRequest request, StreamObserver<AccountIdBatch> responseObserver) {
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : STREAM_PAGE_SIZE;
        String lastSeenId = null;

        while (true) {
            List<String> page = (lastSeenId == null)
                    ? taxLotRepository.findDistinctAccountIdsHoldingSecurityFirstPage(request.getSecurityId(), PageRequest.of(0, pageSize))
                    : taxLotRepository.findDistinctAccountIdsHoldingSecurity(request.getSecurityId(), lastSeenId, PageRequest.of(0, pageSize));

            boolean isLast = page.size() < pageSize;
            responseObserver.onNext(AccountIdBatch.newBuilder().addAllAccountIds(page).setIsLast(isLast).build());

            if (page.isEmpty() || isLast) {
                break;
            }
            lastSeenId = page.get(page.size() - 1);
        }
        responseObserver.onCompleted();
    }

    @Override
    @Transactional(readOnly = true)
    public void getSecurityBySymbol(GetSecurityBySymbolRequest request, StreamObserver<SecurityMsg> responseObserver) {
        Optional<Security> securityOpt = securityRepository.findBySymbol(request.getSymbol());
        if (securityOpt.isEmpty()) {
            responseObserver.onError(Status.NOT_FOUND.withDescription("Security " + request.getSymbol()).asRuntimeException());
            return;
        }
        Security security = securityOpt.get();
        responseObserver.onNext(SecurityMsg.newBuilder()
                .setSecurityId(security.getId())
                .setSymbol(security.getSymbol())
                .setLastPrice(security.getLastPrice().toPlainString())
                .setWashSaleReplacementSymbol(nullToEmpty(security.getWashSaleReplacementSymbol()))
                .build());
        responseObserver.onCompleted();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
