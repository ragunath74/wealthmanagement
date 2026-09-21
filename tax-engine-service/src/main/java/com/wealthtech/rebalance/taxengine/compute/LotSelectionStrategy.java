package com.wealthtech.rebalance.taxengine.compute;

public enum LotSelectionStrategy {
    FIFO,
    LIFO,
    HIFO,       // highest cost basis first -> minimizes realized gain
    TAX_OPTIMAL // losses first (largest loss first), then HIFO among gains
}
