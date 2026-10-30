package com.nexus.core.stock;

/** Pieces taken out of one lot for a sale. */
public record LotTaken(InventoryLot lot, int quantity) {
}
