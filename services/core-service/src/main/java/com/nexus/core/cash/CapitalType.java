package com.nexus.core.cash;

public enum CapitalType {
    /** Cash the business already had when it started using Nexus. */
    OPENING_BALANCE,
    /** The owner puts personal money into the business. */
    CAPITAL_ADDED,
    /** The owner takes personal money back out. */
    CAPITAL_WITHDRAWN,
    /** The owner takes profit out of the business. */
    PROFIT_WITHDRAWN;

    boolean takesCashOut() {
        return this == CAPITAL_WITHDRAWN || this == PROFIT_WITHDRAWN;
    }
}
