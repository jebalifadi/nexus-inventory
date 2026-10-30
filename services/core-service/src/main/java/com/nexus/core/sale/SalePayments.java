package com.nexus.core.sale;

import java.math.BigDecimal;

/**
 * Tells sales how much was paid for them. The payment module implements it, so the sale module
 * does not depend on payment code.
 */
public interface SalePayments {

    BigDecimal paidForSale(long saleId);
}
