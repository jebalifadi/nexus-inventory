package com.nexus.core.payment;

import com.nexus.core.purchase.PurchasePayments;
import com.nexus.core.sale.SalePayments;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/** Answers "how much was paid?" for sales and purchases, from the payments table. */
@Component
class PaymentTotals implements SalePayments, PurchasePayments {

    private final PaymentRepository repository;

    PaymentTotals(PaymentRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal paidForSale(long saleId) {
        return repository.sumForSale(saleId);
    }

    @Override
    public BigDecimal paidForPurchase(long purchaseId) {
        return repository.sumForPurchase(purchaseId);
    }
}
