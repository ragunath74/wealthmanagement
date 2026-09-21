package com.wealthtech.rebalance.account.web;

import com.wealthtech.rebalance.account.domain.ModelPortfolio;
import com.wealthtech.rebalance.account.domain.ModelPortfolioTarget;
import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.repository.ModelPortfolioRepository;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.account.web.dto.CreateModelPortfolioRequest;
import com.wealthtech.rebalance.account.web.dto.ModelPortfolioResponse;
import com.wealthtech.rebalance.account.web.dto.TargetWeightRequest;
import com.wealthtech.rebalance.account.web.dto.UpdateModelPortfolioTargetsRequest;
import com.wealthtech.rebalance.common.event.ModelPortfolioUpdatedEvent;
import com.wealthtech.rebalance.common.kafka.KafkaTopics;
import com.wealthtech.rebalance.common.outbox.OutboxEvent;
import com.wealthtech.rebalance.common.outbox.OutboxEventRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/model-portfolios")
public class ModelPortfolioController {

    private final ModelPortfolioRepository modelPortfolioRepository;
    private final SecurityRepository securityRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ModelPortfolioController(ModelPortfolioRepository modelPortfolioRepository,
                                     SecurityRepository securityRepository,
                                     OutboxEventRepository outboxEventRepository,
                                     ObjectMapper objectMapper) {
        this.modelPortfolioRepository = modelPortfolioRepository;
        this.securityRepository = securityRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ModelPortfolioResponse create(@Valid @RequestBody CreateModelPortfolioRequest request) {
        ModelPortfolio model = new ModelPortfolio();
        model.setName(request.name());
        applyTargets(model, request.targets());
        return ModelPortfolioResponse.from(modelPortfolioRepository.save(model));
    }

    @GetMapping("/{id}")
    public ModelPortfolioResponse get(@PathVariable String id) {
        return modelPortfolioRepository.findWithTargetsById(id).map(ModelPortfolioResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("ModelPortfolio " + id));
    }

    /**
     * Replacing a model's targets is the trigger for a full re-evaluation of every account mapped
     * to it -- potentially hundreds of thousands of accounts. This endpoint bumps the revision
     * and writes an outbox row in the SAME transaction as the target update (rather than calling
     * Kafka directly here) so "the targets changed" and "the fan-out event was queued" can never
     * disagree -- the same transactional-outbox guarantee drift-engine-service and
     * execution-gateway-service use for trade decisions and fills. The actual fan-out (via
     * gRPC-streamed keyset pagination) and per-account trade decisions happen in
     * drift-engine-service, asynchronously, once the relay publishes this row.
     */
    @PutMapping("/{id}/targets")
    @Transactional
    public ModelPortfolioResponse updateTargets(@PathVariable String id, @Valid @RequestBody UpdateModelPortfolioTargetsRequest request) {
        ModelPortfolio model = modelPortfolioRepository.findWithTargetsById(id)
                .orElseThrow(() -> new EntityNotFoundException("ModelPortfolio " + id));

        model.getTargets().clear();
        applyTargets(model, request.targets());
        model.setRevision(model.getRevision() + 1);
        ModelPortfolio saved = modelPortfolioRepository.save(model);

        ModelPortfolioUpdatedEvent event = new ModelPortfolioUpdatedEvent(UUID.randomUUID().toString(), saved.getId(), saved.getRevision());
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setTopic(KafkaTopics.MODEL_PORTFOLIO_UPDATES);
        outboxEvent.setAggregateId(saved.getId());
        outboxEvent.setEventType("MODEL_PORTFOLIO_UPDATED");
        outboxEvent.setPayload(objectMapper.writeValueAsString(event));
        outboxEventRepository.save(outboxEvent);

        return ModelPortfolioResponse.from(saved);
    }

    private void applyTargets(ModelPortfolio model, List<TargetWeightRequest> requests) {
        for (TargetWeightRequest t : requests) {
            Security security = securityRepository.findBySymbol(t.symbol())
                    .orElseThrow(() -> new EntityNotFoundException("Security " + t.symbol()));
            ModelPortfolioTarget target = new ModelPortfolioTarget();
            target.setModelPortfolio(model);
            target.setSecurity(security);
            target.setTargetWeight(t.targetWeight());
            model.getTargets().add(target);
        }
    }
}
