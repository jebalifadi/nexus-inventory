package com.nexus.core.stock;

/** How many pieces of a product are in stock at one location. */
public record StockLevel(Long productId, String productName, Long locationId, String locationName, long quantity) {
}
