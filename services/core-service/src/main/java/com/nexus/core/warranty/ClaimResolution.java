package com.nexus.core.warranty;

public enum ClaimResolution {
    /** Fixed for the customer; the repair cost lowers the sale's profit. */
    REPAIRED,
    /** Exchanged for a working item; its cost lowers the sale's profit. */
    REPLACED,
    /** Goods taken back and money given back. */
    REFUNDED,
    REJECTED
}
