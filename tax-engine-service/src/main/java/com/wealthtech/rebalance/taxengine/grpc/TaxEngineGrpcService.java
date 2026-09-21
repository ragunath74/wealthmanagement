package com.wealthtech.rebalance.taxengine.grpc;

import com.wealthtech.rebalance.grpc.account.ModelTargetMsg;
import com.wealthtech.rebalance.grpc.account.SecurityMsg;
import com.wealthtech.rebalance.grpc.account.TaxLotMsg;
import com.wealthtech.rebalance.grpc.taxengine.*;
import com.wealthtech.rebalance.taxengine.compute.*;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * gRPC server implementation for tax-engine-service. Pure request -> compute -> response; no
 * field here is ever read from or written to a database, which is what makes this service
 * trivially horizontally scalable (any replica can answer any request, statelessly) and trivially
 * safe to retry (idempotent by construction -- the same request always produces the same trade
 * list, so drift-engine-service doesn't even need special retry logic calling this RPC beyond
 * gRPC's own retry/deadline support).
 */
@Service
public class TaxEngineGrpcService extends TaxOptimizationServiceGrpc.TaxOptimizationServiceImplBase {

    private final TradeGenerationService tradeGenerationService;

    public TaxEngineGrpcService(TradeGenerationService tradeGenerationService) {
        this.tradeGenerationService = tradeGenerationService;
    }

    @Override
    public void generateTrades(GenerateTradesRequest request, StreamObserver<GenerateTradesResponse> responseObserver) {
        Map<String, SecurityView> replacementBySymbol = new LinkedHashMap<>();
        for (SecurityMsg s : request.getReplacementSecuritiesList()) {
            replacementBySymbol.put(s.getSymbol(), toSecurityView(s));
        }

        List<LotView> openLots = request.getOpenLotsList().stream().map(this::toLotView).toList();
        List<ModelTargetView> modelTargets = request.getModelTargetsList().stream().map(this::toModelTargetView).toList();

        TradeGenerationService.RebalancePolicy policy = new TradeGenerationService.RebalancePolicy(
                new BigDecimal(request.getDriftThresholdBps()),
                new BigDecimal(request.getMinTradeAmount()),
                new BigDecimal(request.getCashBufferPct()),
                request.getWashSaleWindowDays(),
                new BigDecimal(request.getMinHarvestLoss()),
                LotSelectionStrategy.valueOf(request.getLotSelectionStrategy()));

        List<GeneratedTrade> trades = tradeGenerationService.generateTrades(
                request.getTaxable(),
                request.getTaxLossHarvestingEnabled(),
                new BigDecimal(request.getCashBalance()),
                openLots,
                modelTargets,
                replacementBySymbol,
                policy,
                LocalDate.parse(request.getAsOfDate()));

        GenerateTradesResponse.Builder responseBuilder = GenerateTradesResponse.newBuilder();
        for (GeneratedTrade trade : trades) {
            GeneratedTradeMsg.Builder tradeMsg = GeneratedTradeMsg.newBuilder()
                    .setSecurityId(trade.security().id())
                    .setSymbol(trade.security().symbol())
                    .setSide(trade.side().name())
                    .setQuantity(trade.quantity().toPlainString())
                    .setReferencePrice(trade.referencePrice().toPlainString())
                    .setReason(trade.reason());
            for (LotSaleAllocation allocation : trade.lotAllocations()) {
                tradeMsg.addLotAllocations(LotAllocationMsg.newBuilder()
                        .setLotId(allocation.lot().lotId())
                        .setQuantity(allocation.quantity().toPlainString())
                        .setRealizedGainLoss(allocation.realizedGainLoss().toPlainString())
                        .setLongTerm(allocation.longTerm())
                        .build());
            }
            responseBuilder.addTrades(tradeMsg.build());
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }

    private SecurityView toSecurityView(SecurityMsg s) {
        return new SecurityView(s.getSecurityId(), s.getSymbol(), new BigDecimal(s.getLastPrice()), s.getWashSaleReplacementSymbol());
    }

    private LotView toLotView(TaxLotMsg lotMsg) {
        SecurityView security = new SecurityView(lotMsg.getSecurityId(), lotMsg.getSymbol(),
                new BigDecimal(lotMsg.getLastPrice()), lotMsg.getWashSaleReplacementSymbol());
        return new LotView(lotMsg.getLotId(), security, new BigDecimal(lotMsg.getQuantity()),
                new BigDecimal(lotMsg.getCostBasisPerShare()), LocalDate.parse(lotMsg.getAcquiredDate()));
    }

    private ModelTargetView toModelTargetView(ModelTargetMsg targetMsg) {
        SecurityView security = new SecurityView(targetMsg.getSecurityId(), targetMsg.getSymbol(),
                new BigDecimal(targetMsg.getLastPrice()), targetMsg.getWashSaleReplacementSymbol());
        return new ModelTargetView(security, new BigDecimal(targetMsg.getTargetWeight()));
    }
}
