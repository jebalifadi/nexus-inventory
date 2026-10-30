package com.nexus.core.shipment;

/** How a shipment's cost is split across the goods inside. */
public enum AllocationMethod {
    /** In proportion to what each item cost: a 300 EUR phone carries three times more than a 100 EUR one. */
    BY_VALUE,
    /** The same share for every piece. */
    BY_QUANTITY
}
