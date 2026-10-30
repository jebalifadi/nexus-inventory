package com.nexus.core.sale;

/**
 * What the owner enters for one sale line.
 *
 * <p>For goods tracked per item, give the {@code unitId} (U-…). For goods tracked by quantity, give
 * the {@code productId} and {@code quantity}; Nexus picks the lots itself, oldest first.
 */
public record NewSaleLine(
        String unitId,
        Long productId,
        Integer quantity,
        String unitPrice,
        Integer warrantyDays,
        String warrantyTerms) {
}
