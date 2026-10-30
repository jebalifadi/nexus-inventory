package com.nexus.core.sale;

import com.nexus.core.money.Money;

/** Pieces of a quantity line taken from one lot, with the real cost of one piece at that moment. */
public record SaleAllocation(String lotId, int quantity, Money unitCost) {
}
