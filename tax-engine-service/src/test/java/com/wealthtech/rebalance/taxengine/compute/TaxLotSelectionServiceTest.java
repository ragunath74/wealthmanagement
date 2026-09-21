package com.wealthtech.rebalance.taxengine.compute;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaxLotSelectionServiceTest {

    private final TaxLotSelectionService service = new TaxLotSelectionService();
    private final SecurityView vti = new SecurityView("s1", "VTI", new BigDecimal("100"), null);

    private LotView lot(String qty, String basis, LocalDate acquired) {
        return new LotView("lot-" + acquired, vti, new BigDecimal(qty), new BigDecimal(basis), acquired);
    }

    @Test
    void fifoSellsOldestLotFirst() {
        LocalDate today = LocalDate.now();
        LotView oldLot = lot("5", "50", today.minusYears(3));
        LotView newLot = lot("5", "90", today.minusMonths(1));

        List<LotSaleAllocation> allocations = service.selectLotsForSale(
                List.of(newLot, oldLot), new BigDecimal("5"), new BigDecimal("100"), LotSelectionStrategy.FIFO, today);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).lot()).isEqualTo(oldLot);
    }

    @Test
    void lifoSellsNewestLotFirst() {
        LocalDate today = LocalDate.now();
        LotView oldLot = lot("5", "50", today.minusYears(3));
        LotView newLot = lot("5", "90", today.minusMonths(1));

        List<LotSaleAllocation> allocations = service.selectLotsForSale(
                List.of(oldLot, newLot), new BigDecimal("5"), new BigDecimal("100"), LotSelectionStrategy.LIFO, today);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).lot()).isEqualTo(newLot);
    }

    @Test
    void hifoSellsHighestCostBasisFirstToMinimizeGain() {
        LocalDate today = LocalDate.now();
        LotView cheapLot = lot("5", "20", today.minusYears(3));
        LotView expensiveLot = lot("5", "95", today.minusMonths(1));

        List<LotSaleAllocation> allocations = service.selectLotsForSale(
                List.of(cheapLot, expensiveLot), new BigDecimal("5"), new BigDecimal("100"), LotSelectionStrategy.HIFO, today);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).lot()).isEqualTo(expensiveLot);
        assertThat(allocations.get(0).realizedGainLoss()).isEqualByComparingTo("25");
    }

    @Test
    void taxOptimalRealizesLossesBeforeGains() {
        LocalDate today = LocalDate.now();
        LotView gainLot = lot("5", "20", today.minusYears(3));
        LotView lossLot = lot("5", "150", today.minusMonths(1));

        List<LotSaleAllocation> allocations = service.selectLotsForSale(
                List.of(gainLot, lossLot), new BigDecimal("5"), new BigDecimal("100"), LotSelectionStrategy.TAX_OPTIMAL, today);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).lot()).isEqualTo(lossLot);
        assertThat(allocations.get(0).realizedGainLoss()).isNegative();
    }

    @Test
    void insufficientQuantityThrows() {
        LocalDate today = LocalDate.now();
        LotView lot1 = lot("2", "50", today.minusYears(1));

        assertThatThrownBy(() -> service.selectLotsForSale(
                List.of(lot1), new BigDecimal("5"), new BigDecimal("100"), LotSelectionStrategy.FIFO, today))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
