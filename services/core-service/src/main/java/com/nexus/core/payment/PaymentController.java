package com.nexus.core.payment;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.web.MoneyJson;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for payments, receivables and payables. */
@RestController
@RequestMapping("/api/v1")
class PaymentController {

    record NewPayment(MoneyJson amount, LocalDate paymentDate, PaymentMethod method, String notes) {
    }

    record PaymentView(String id, PaymentDirection direction, String reference, MoneyJson amount,
                       LocalDate paymentDate, PaymentMethod method, String notes) {

        static PaymentView of(Payment p) {
            return new PaymentView(p.publicId(), p.direction(), p.reference(), MoneyJson.of(p.amount()),
                    p.paymentDate(), p.method(), p.notes());
        }
    }

    record OpenBalanceView(String reference, String counterparty, LocalDate date, long daysOpen, MoneyJson total,
                           MoneyJson paid, MoneyJson balance) {

        static OpenBalanceView of(OpenBalance b) {
            return new OpenBalanceView(b.reference(), b.counterparty(), b.date(), b.daysOpen(),
                    MoneyJson.of(b.total()), MoneyJson.of(b.paid()), MoneyJson.of(b.balance()));
        }
    }

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/sales/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentView paySale(@PathVariable("id") String id, @RequestBody NewPayment request) {
        return PaymentView.of(payments.recordSalePayment(id, amount(request), request.paymentDate(),
                request.method(), request.notes()));
    }

    @GetMapping("/sales/{id}/payments")
    List<PaymentView> salePayments(@PathVariable("id") String id) {
        return payments.paymentsOfSale(id).stream().map(PaymentView::of).toList();
    }

    @PostMapping("/purchases/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    PaymentView payPurchase(@PathVariable("id") String id, @RequestBody NewPayment request) {
        return PaymentView.of(payments.recordPurchasePayment(id, amount(request), request.paymentDate(),
                request.method(), request.notes()));
    }

    @GetMapping("/purchases/{id}/payments")
    List<PaymentView> purchasePayments(@PathVariable("id") String id) {
        return payments.paymentsOfPurchase(id).stream().map(PaymentView::of).toList();
    }

    @GetMapping("/receivables")
    List<OpenBalanceView> receivables() {
        return payments.receivables().stream().map(OpenBalanceView::of).toList();
    }

    @GetMapping("/payables")
    List<OpenBalanceView> payables() {
        return payments.payables().stream().map(OpenBalanceView::of).toList();
    }

    private static com.nexus.core.money.Money amount(NewPayment request) {
        if (request == null || request.amount() == null) {
            throw new BusinessRuleException("amount is required");
        }
        return request.amount().toMoney();
    }
}
