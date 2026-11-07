package com.nexus.core.stock;

/**
 * Published when a unit or a lot comes back from the trash, so other modules can react (a write-off
 * is cancelled) without the stock module depending on them. Exactly one of the two IDs is set.
 */
public record StockRestored(Long unitId, Long lotId) {
}
