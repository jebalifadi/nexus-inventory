package com.nexus.core.warranty;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/** A customer's complaint about goods they bought, and how it was handled. */
public record WarrantyClaim(
        Long id,
        String publicId,
        String saleId,
        int lineNo,
        String unitId,
        int quantity,
        LocalDate claimDate,
        String issue,
        boolean withinWarranty,
        LocalDate warrantyEndDate,
        String goodwillReason,
        ClaimStatus status,
        ClaimResolution resolution,
        Money cost,
        Money refund,
        LocalDate resolvedDate,
        String notes) {
}
