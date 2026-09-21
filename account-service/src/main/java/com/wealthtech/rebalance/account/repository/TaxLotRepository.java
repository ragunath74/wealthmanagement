package com.wealthtech.rebalance.account.repository;

import com.wealthtech.rebalance.account.domain.TaxLot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaxLotRepository extends JpaRepository<TaxLot, String> {

    @EntityGraph(attributePaths = {"security"})
    List<TaxLot> findByAccountIdAndClosedFalse(String accountId);

    Optional<TaxLot> findByIdAndAccountId(String lotId, String accountId);

    /** Keyset-paginated distinct account ids currently holding a given security -- backs ListAccountIdsHoldingSecurity. */
    @Query("select distinct t.account.id from TaxLot t where t.security.id = :securityId and t.closed = false "
            + "and t.account.id > :lastSeenId order by t.account.id asc")
    List<String> findDistinctAccountIdsHoldingSecurity(@Param("securityId") String securityId, @Param("lastSeenId") String lastSeenId, Pageable pageable);

    @Query("select distinct t.account.id from TaxLot t where t.security.id = :securityId and t.closed = false "
            + "order by t.account.id asc")
    List<String> findDistinctAccountIdsHoldingSecurityFirstPage(@Param("securityId") String securityId, Pageable pageable);
}
