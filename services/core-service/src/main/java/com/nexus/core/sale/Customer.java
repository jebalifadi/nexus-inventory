package com.nexus.core.sale;

/** Someone the business sells to. */
public record Customer(Long id, String name, String phone, String email, String country, String notes) {
}
