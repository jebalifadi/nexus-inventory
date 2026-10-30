package com.nexus.core.payment;

/** A normal payment, or money given back to a customer. Amounts stay positive; the kind gives the sign. */
public enum PaymentKind {
    PAYMENT,
    REFUND
}
