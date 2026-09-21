package com.wealthtech.rebalance.taxengine.compute;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Decides which specific tax lots to draw down when selling a given quantity of a security. */
@Service
public class TaxLotSelectionService {

    public List<LotSaleAllocation> selectLotsForSale(List<LotView> openLots,
                                                       BigDecimal quantityToSell,
                                                       BigDecimal price,
                                                       LotSelectionStrategy strategy,
                                                       LocalDate asOf) {
        List<LotView> ordered = orderLots(openLots, strategy, price);
        List<LotSaleAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = quantityToSell;

        for (LotView lot : ordered) {
            if (remaining.signum() <= 0) break;
            BigDecimal available = lot.quantity();
            if (available.signum() <= 0) continue;

            BigDecimal qtyFromLot = available.min(remaining);
            BigDecimal gainLoss = price.subtract(lot.costBasisPerShare()).multiply(qtyFromLot);
            allocations.add(new LotSaleAllocation(lot, qtyFromLot, gainLoss, lot.isLongTerm(asOf)));
            remaining = remaining.subtract(qtyFromLot);
        }

        if (remaining.signum() > 0) {
            throw new IllegalArgumentException(
                    "Insufficient open lot quantity to sell " + quantityToSell + "; short by " + remaining);
        }
        return allocations;
    }

    private List<LotView> orderLots(List<LotView> openLots, LotSelectionStrategy strategy, BigDecimal price) {
        List<LotView> lots = new ArrayList<>(openLots);
        Comparator<LotView> comparator = switch (strategy) {
            case FIFO -> Comparator.comparing(LotView::acquiredDate);
            case LIFO -> Comparator.comparing(LotView::acquiredDate).reversed();
            // Highest cost basis first: sold first, it recognizes the smallest gain / largest loss.
            case HIFO -> Comparator.comparing(LotView::costBasisPerShare).reversed();
            // Realize losses first (largest loss first) to maximize harvestable loss this run;
            // once every lot is at a gain, fall back to HIFO to minimize the gain recognized.
            case TAX_OPTIMAL -> Comparator
                    .comparing((LotView lot) -> price.subtract(lot.costBasisPerShare())) // most negative (biggest loss) first
                    .thenComparing(Comparator.comparing(LotView::costBasisPerShare).reversed());
        };
        lots.sort(comparator);
        return lots;
    }
}
