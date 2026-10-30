package com.nexus.core.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.stock.FifoAllocator.LotStock;
import com.nexus.core.stock.FifoAllocator.Take;
import java.util.List;
import org.junit.jupiter.api.Test;

class FifoAllocatorTest {

    @Test
    void takesFromTheOldestLotFirst() {
        List<Take> takes = FifoAllocator.allocate(List.of(new LotStock(1, 4), new LotStock(2, 10)), 6);

        assertThat(takes).containsExactly(new Take(1, 4), new Take(2, 2));
    }

    @Test
    void staysInTheFirstLotWhenItIsEnough() {
        assertThat(FifoAllocator.allocate(List.of(new LotStock(1, 4), new LotStock(2, 10)), 3))
                .containsExactly(new Take(1, 3));
    }

    @Test
    void skipsEmptyLots() {
        assertThat(FifoAllocator.allocate(List.of(new LotStock(1, 0), new LotStock(2, 5)), 5))
                .containsExactly(new Take(2, 5));
    }

    @Test
    void refusesWhenThereIsNotEnough() {
        assertThatThrownBy(() -> FifoAllocator.allocate(List.of(new LotStock(1, 4)), 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only 4 available");
    }
}
