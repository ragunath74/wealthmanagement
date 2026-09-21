package com.wealthtech.rebalance.account.web;

import com.wealthtech.rebalance.account.domain.Advisor;
import com.wealthtech.rebalance.account.repository.AdvisorRepository;
import com.wealthtech.rebalance.account.web.dto.AdvisorResponse;
import com.wealthtech.rebalance.account.web.dto.CreateAdvisorRequest;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/advisors")
public class AdvisorController {

    private final AdvisorRepository advisorRepository;

    public AdvisorController(AdvisorRepository advisorRepository) {
        this.advisorRepository = advisorRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdvisorResponse create(@Valid @RequestBody CreateAdvisorRequest request) {
        Advisor advisor = new Advisor();
        advisor.setDisplayName(request.displayName());
        advisor.setEmail(request.email());
        System.out.println("test");
        return AdvisorResponse.from(advisorRepository.save(advisor));
    }

    @GetMapping("/{id}")
    public AdvisorResponse get(@PathVariable String id) {
        return advisorRepository.findById(id).map(AdvisorResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Advisor " + id));
    }
}
