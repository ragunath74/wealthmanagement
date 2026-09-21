package com.wealthtech.rebalance.account.repository;

import com.wealthtech.rebalance.account.domain.ClientAccount;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientAccountRepository extends JpaRepository<ClientAccount, String> {

    /**
     * Keyset ("seek") pagination instead of OFFSET-based paging: at 2M+ accounts an OFFSET query
     * degrades linearly as the offset grows because the database still has to walk and discard
     * every preceding row. Ordering by primary key and filtering id > lastSeenId keeps every page
     * an index range scan regardless of how deep the scan has progressed. This backs the
     * server-streaming gRPC method AccountService.ListAccountIdsForModel.
     */
    List<ClientAccount> findByModelPortfolioIdAndIdGreaterThanOrderByIdAsc(String modelPortfolioId, String lastSeenId, Pageable pageable);

    List<ClientAccount> findByModelPortfolioIdOrderByIdAsc(String modelPortfolioId, Pageable pageable);
}
