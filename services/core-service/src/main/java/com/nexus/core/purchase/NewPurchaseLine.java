package com.nexus.core.purchase;

import java.util.List;
import java.util.Map;

/**
 * What the owner enters for one purchase line.
 *
 * <p>For goods tracked per item (phones), {@code items} holds each piece's own values, such as
 * battery health or IMEI; the quantity is then the number of items. For goods tracked by quantity
 * (sneakers), only {@code quantity} is used.
 */
public record NewPurchaseLine(
        Long productId,
        Long locationId,
        Integer quantity,
        String unitPrice,
        List<Map<String, String>> items) {
}
