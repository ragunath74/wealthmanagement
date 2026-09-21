package com.wealthtech.rebalance.taxengine.compute;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LotView(
        String lotId,
        SecurityView security,
        BigDecimal quantity,
        BigDecimal costBasisPerShare,
        LocalDate acquiredDate
) {
    public boolean isLongTerm(LocalDate asOf) {
        return acquiredDate.plusYears(1).isBefore(asOf) || acquiredDate.plusYears(1).isEqual(asOf);
    }
}
