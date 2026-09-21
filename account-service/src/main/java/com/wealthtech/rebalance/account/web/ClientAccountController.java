package com.wealthtech.rebalance.account.web;

import com.wealthtech.rebalance.account.domain.Advisor;
import com.wealthtech.rebalance.account.domain.ClientAccount;
import com.wealthtech.rebalance.account.domain.ModelPortfolio;
import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.domain.TaxLot;
import com.wealthtech.rebalance.account.repository.AdvisorRepository;
import com.wealthtech.rebalance.account.repository.ClientAccountRepository;
import com.wealthtech.rebalance.account.repository.ModelPortfolioRepository;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.account.repository.TaxLotRepository;
import com.wealthtech.rebalance.account.web.dto.AccountResponse;
import com.wealthtech.rebalance.account.web.dto.AddTaxLotRequest;
import com.wealthtech.rebalance.account.web.dto.CreateAccountRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/accounts")
public class ClientAccountController {

    private final ClientAccountRepository clientAccountRepository;
    private final AdvisorRepository advisorRepository;
    private final ModelPortfolioRepository modelPortfolioRepository;
    private final TaxLotRepository taxLotRepository;
    private final SecurityRepository securityRepository;

    public ClientAccountController(ClientAccountRepository clientAccountRepository,
                                    AdvisorRepository advisorRepository,
                                    ModelPortfolioRepository modelPortfolioRepository,
                                    TaxLotRepository taxLotRepository,
                                    SecurityRepository securityRepository) {
        this.clientAccountRepository = clientAccountRepository;
        this.advisorRepository = advisorRepository;
        this.modelPortfolioRepository = modelPortfolioRepository;
        this.taxLotRepository = taxLotRepository;
        this.securityRepository = securityRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Advisor advisor = advisorRepository.findById(request.advisorId())
                .orElseThrow(() -> new EntityNotFoundException("Advisor " + request.advisorId()));

        ClientAccount account = new ClientAccount();
        account.setAdvisor(advisor);
        account.setCashBalance(request.initialCashBalance());
        account.setTaxable(request.taxable());
        account.setTaxLossHarvestingEnabled(request.taxLossHarvestingEnabled());

        if (request.modelPortfolioId() != null) {
            ModelPortfolio model = modelPortfolioRepository.findById(request.modelPortfolioId())
                    .orElseThrow(() -> new EntityNotFoundException("ModelPortfolio " + request.modelPortfolioId()));
            account.setModelPortfolio(model);
        }
        return AccountResponse.from(clientAccountRepository.save(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable String id) {
        return clientAccountRepository.findById(id).map(AccountResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("ClientAccount " + id));
    }

    /** Demo/seeding endpoint: opens a tax lot directly so the pipeline can be exercised without a full brokerage-feed integration. */
    @PostMapping("/{id}/lots")
    @ResponseStatus(HttpStatus.CREATED)
    public void addLot(@PathVariable String id, @Valid @RequestBody AddTaxLotRequest request) {
        ClientAccount account = clientAccountRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ClientAccount " + id));
        Security security = securityRepository.findBySymbol(request.symbol())
                .orElseThrow(() -> new EntityNotFoundException("Security " + request.symbol()));

        TaxLot lot = new TaxLot();
        lot.setAccount(account);
        lot.setSecurity(security);
        lot.setQuantity(request.quantity());
        lot.setCostBasisPerShare(request.costBasisPerShare());
        lot.setAcquiredDate(request.acquiredDate());
        taxLotRepository.save(lot);
    }
}
