package com.nexus.core.stock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnitStatusTest {

    @Test
    void followsTheNormalPathFromStockToClosed() {
        assertThat(UnitStatus.IN_INVENTORY.canMoveTo(UnitStatus.LISTED)).isTrue();
        assertThat(UnitStatus.LISTED.canMoveTo(UnitStatus.SOLD)).isTrue();
        assertThat(UnitStatus.SOLD.canMoveTo(UnitStatus.CLOSED)).isTrue();
    }

    @Test
    void refusesImpossibleSteps() {
        assertThat(UnitStatus.SOLD.canMoveTo(UnitStatus.LISTED)).isFalse();
        assertThat(UnitStatus.SOLD.canMoveTo(UnitStatus.SOLD)).isFalse();
        assertThat(UnitStatus.CANCELLED.canMoveTo(UnitStatus.IN_INVENTORY)).isFalse();
    }

    @Test
    void returnedItemsGoBackToStock() {
        assertThat(UnitStatus.CLOSED.canMoveTo(UnitStatus.RETURNED)).isTrue();
        assertThat(UnitStatus.RETURNED.canMoveTo(UnitStatus.IN_INVENTORY)).isTrue();
    }

    @Test
    void onlyAvailableStatusesCountAsInStock() {
        assertThat(UnitStatus.LISTED.isInStock()).isTrue();
        assertThat(UnitStatus.SOLD.isInStock()).isFalse();
        assertThat(UnitStatus.IN_TRANSIT.isInStock()).isFalse();
    }
}
