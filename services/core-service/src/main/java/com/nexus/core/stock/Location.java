package com.nexus.core.stock;

/** A place where stock is kept, for example "Berlin storage" or "Tunis shop". */
public record Location(Long id, String name, String country) {
}
