package com.nexus.core.payment;

import com.nexus.core.money.Money;
import com.nexus.core.purchase.Purchase;
import com.nexus.core.purchase.PurchaseService;
import com.nexus.core.sale.Sale;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.shared.NotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of payments: customers paying sales, the business paying suppliers, and what is
 * still open on both sides.
 */
@Service
public class PaymentService {

    private final PaymentRepository repository;
    private final SaleService sales;
    private final PurchaseService purchases;

    PaymentService(PaymentRepository repository, SaleService sales, PurchaseService purchases) {
        this.repository = repository;
        this.sales = sales;
        this.purchases = purchases;
    }

    /** A customer pays all or part of a sale. Paying more than what is still owed is refused. */
    @Transactional
    public Payment recordSalePayment(String salePublicId, Money amount, LocalDate paymentDate, PaymentMethod method,
                                     String notes) {
        long saleId = repository.lockSale(salePublicId)
                .orElseThrow(() -> new NotFoundException("Sale " + salePublicId + " not found"));
        Sale sale = sales.getSale(salePublicId);
        requireValid(amount, sale.balance(), salePublicId);
        long id = repository.insertPayment(PaymentDirection.INCOMING, saleId, null, amount.amount(),
                amount.currency().getCurrencyCode(), dateOrToday(paymentDate), methodOrCash(method),
                blankToNull(notes));
        return toPayment(repository.findPayment(id).orElseThrow());
    }

    /** The business pays a supplier all or part of a purchase. */
    @Transactional
    public Payment recordPurchasePayment(String purchasePublicId, Money amount, LocalDate paymentDate,
                                         PaymentMethod method, String notes) {
        long purchaseId = repository.lockPurchase(purchasePublicId)
                .orElseThrow(() -> new NotFoundException("Purchase " + purchasePublicId + " not found"));
        Purchase purchase = purchases.getPurchase(purchasePublicId);
        requireValid(amount, purchase.balance(), purchasePublicId);
        long id = repository.insertPayment(PaymentDirection.OUTGOING, null, purchaseId, amount.amount(),
                amount.currency().getCurrencyCode(), dateOrToday(paymentDate), methodOrCash(method),
                blankToNull(notes));
        return toPayment(repository.findPayment(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsOfSale(String salePublicId) {
        Sale sale = sales.getSale(salePublicId);
        return repository.findBySale(sale.id()).stream().map(PaymentService::toPayment).toList();
    }

    @Transactional(readOnly = true)
    public List<Payment> paymentsOfPurchase(String purchasePublicId) {
        Purchase purchase = purchases.getPurchase(purchasePublicId);
        return repository.findByPurchase(purchase.id()).stream().map(PaymentService::toPayment).toList();
    }

    /** Money customers still owe the business, oldest first. Never counted as cash. */
    @Transactional(readOnly = true)
    public List<OpenBalance> receivables() {
        LocalDate today = LocalDate.now();
        return sales.listSales().stream()
                .filter(sale -> sale.balance().amount().signum() > 0)
                .map(sale -> new OpenBalance(sale.publicId(),
                        sale.customerName() == null ? "Walk-in customer" : sale.customerName(), sale.saleDate(),
                        ChronoUnit.DAYS.between(sale.saleDate(), today), sale.total(), sale.paid(), sale.balance()))
                .sorted(Comparator.comparing(OpenBalance::date))
                .toList();
    }

    /** Money the business still owes its suppliers, oldest first. */
    @Transactional(readOnly = true)
    public List<OpenBalance> payables() {
        LocalDate today = LocalDate.now();
        return purchases.listPurchases().stream()
                .filter(purchase -> purchase.balance().amount().signum() > 0)
                .map(purchase -> new OpenBalance(purchase.publicId(), purchase.supplierName(),
                        purchase.purchaseDate(), ChronoUnit.DAYS.between(purchase.purchaseDate(), today),
                        purchase.total(), purchase.paid(), purchase.balance()))
                .sorted(Comparator.comparing(OpenBalance::date))
                .toList();
    }

    private static void requireValid(Money amount, Money balance, String reference) {
        if (amount == null) {
            throw new BusinessRuleException("Amount is required");
        }
        if (amount.amount().signum() <= 0) {
            throw new BusinessRuleException("Amount must be more than zero");
        }
        if (!amount.currency().equals(balance.currency())) {
            throw new BusinessRuleException("Payment must be in " + balance.currency().getCurrencyCode()
                    + ", the currency of " + reference + " (other currencies come with exchange rates)");
        }
        if (amount.amount().compareTo(balance.amount()) > 0) {
            throw new BusinessRuleException("Payment of " + amount + " is more than the open balance of "
                    + balance + " on " + reference);
        }
    }

    private static Payment toPayment(PaymentRepository.PaymentRow row) {
        return new Payment(row.id(), row.publicId(), row.direction(), row.reference(),
                Money.of(row.amount().toPlainString(), row.currency()), row.paymentDate(), row.method(), row.notes());
    }

    private static LocalDate dateOrToday(LocalDate date) {
        return date == null ? LocalDate.now() : date;
    }

    private static PaymentMethod methodOrCash(PaymentMethod method) {
        return method == null ? PaymentMethod.CASH : method;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
