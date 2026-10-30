package com.nexus.core.catalog;

/** How stock of a category is tracked: one record per physical item, or by quantity. */
public enum TrackingMode {
    UNIT,
    LOT
}
