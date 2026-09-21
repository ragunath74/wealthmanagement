package com.wealthtech.rebalance.account.web;

import com.wealthtech.rebalance.account.domain.Security;
import com.wealthtech.rebalance.account.repository.SecurityRepository;
import com.wealthtech.rebalance.account.web.dto.CreateSecurityRequest;
import com.wealthtech.rebalance.account.web.dto.SecurityResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/securities")
public class SecurityController {

    private final SecurityRepository securityRepository;

    public SecurityController(SecurityRepository securityRepository) {
        this.securityRepository = securityRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityResponse create(@Valid @RequestBody CreateSecurityRequest request) {
        Security security = new Security();
        security.setSymbol(request.symbol());
        security.setName(request.name());
        security.setLastPrice(request.initialPrice());
        security.setLastPriceAt(Instant.now());
        security.setWashSaleReplacementSymbol(request.washSaleReplacementSymbol());
        return SecurityResponse.from(securityRepository.save(security));
    }

    @GetMapping("/{symbol}")
    public SecurityResponse get(@PathVariable String symbol) {
        return securityRepository.findBySymbol(symbol).map(SecurityResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Security " + symbol));
    }
}
