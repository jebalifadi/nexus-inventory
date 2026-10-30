package com.nexus.core.shared;

import com.nexus.core.money.Money;

/** How far a sale or purchase is paid. Always calculated from the payments, never set by hand. */
public enum PaymentStatus {
    PENDING,
    PARTIALLY_PAID,
    PAID;

    public static PaymentStatus of(Money total, Money paid) {
        if (paid.amount().compareTo(total.amount()) >= 0) {
            return PAID;
        }
        return paid.amount().signum() == 0 ? PENDING : PARTIALLY_PAID;
    }
}
