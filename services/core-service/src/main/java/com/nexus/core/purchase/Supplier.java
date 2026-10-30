package com.nexus.core.purchase;

/** A company or person the business buys goods from, in any country. */
public record Supplier(Long id, String name, String country, String contact, String notes) {
}
