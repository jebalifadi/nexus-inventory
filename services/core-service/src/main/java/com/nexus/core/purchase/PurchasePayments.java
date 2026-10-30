package com.nexus.core.purchase;

import java.math.BigDecimal;

/**
 * Tells purchases how much was paid to the supplier. The payment module implements it, so the
 * purchase module does not depend on payment code.
 */
public interface PurchasePayments {

    BigDecimal paidForPurchase(long purchaseId);
}
