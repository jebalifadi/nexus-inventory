package com.nexus.core.payment;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/**
 * One payment, in or out.
 *
 * @param reference the sale (S-…) or purchase (P-…) it pays
 */
public record Payment(Long id, String publicId, PaymentDirection direction, String reference, Money amount,
                      LocalDate paymentDate, PaymentMethod method, String notes, PaymentKind kind) {
}
