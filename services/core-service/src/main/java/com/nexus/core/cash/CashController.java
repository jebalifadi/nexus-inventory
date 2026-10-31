package com.nexus.core.cash;

import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.web.MoneyJson;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for cash, capital and the business overview. */
@RestController
@RequestMapping("/api/v1")
class CashController {

    record NewCapital(CapitalType type, MoneyJson amount, LocalDate date, String notes) {
    }

    record NewExchange(MoneyJson from, MoneyJson to, LocalDate date, String notes) {
    }

    record CapitalView(String id, CapitalType type, MoneyJson amount, LocalDate date, String notes) {

        static CapitalView of(CapitalMovement m) {
            return new CapitalView(m.publicId(), m.type(), MoneyJson.of(m.amount()), m.date(), m.notes());
        }
    }

    record ExchangeView(String id, MoneyJson from, MoneyJson to, LocalDate date, String notes) {

        static ExchangeView of(CurrencyExchange e) {
            return new ExchangeView(e.publicId(), MoneyJson.of(e.from()), MoneyJson.of(e.to()), e.date(), e.notes());
        }
    }

    record MovementView(LocalDate date, String type, String reference, MoneyJson amount) {

        static MovementView of(CashMovement m) {
            return new MovementView(m.date(), m.type(), m.reference(), MoneyJson.of(m.amount()));
        }
    }

    record CapitalSummaryView(String currency, MoneyJson added, MoneyJson withdrawn, MoneyJson netContribution,
                              MoneyJson profitWithdrawn) {

        static CapitalSummaryView of(CapitalSummary s) {
            return new CapitalSummaryView(s.currency(), MoneyJson.of(s.added()), MoneyJson.of(s.withdrawn()),
                    MoneyJson.of(s.netContribution()), MoneyJson.of(s.profitWithdrawn()));
        }
    }

    record PositionView(String currency, MoneyJson cash, MoneyJson stockValue, MoneyJson receivables,
                        MoneyJson payables, MoneyJson businessValue) {

        static PositionView of(CurrencyPosition p) {
            return new PositionView(p.currency(), MoneyJson.of(p.cash()), MoneyJson.of(p.stockValue()),
                    MoneyJson.of(p.receivables()), MoneyJson.of(p.payables()), MoneyJson.of(p.businessValue()));
        }
    }

    private final CashService cash;

    CashController(CashService cash) {
        this.cash = cash;
    }

    @PostMapping("/capital")
    @ResponseStatus(HttpStatus.CREATED)
    CapitalView recordCapital(@RequestBody NewCapital request) {
        if (request.amount() == null) {
            throw new BusinessRuleException("amount is required");
        }
        return CapitalView.of(cash.recordCapital(request.type(), request.amount().toMoney(), request.date(),
                request.notes()));
    }

    @GetMapping("/capital")
    List<CapitalSummaryView> capital() {
        return cash.capital().stream().map(CapitalSummaryView::of).toList();
    }

    @PostMapping("/currency-exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    ExchangeView exchange(@RequestBody NewExchange request) {
        if (request.from() == null || request.to() == null) {
            throw new BusinessRuleException("from and to are required");
        }
        return ExchangeView.of(cash.exchange(request.from().toMoney(), request.to().toMoney(), request.date(),
                request.notes()));
    }

    @GetMapping("/cash-book")
    List<MovementView> cashBook() {
        return cash.cashBook().stream().map(MovementView::of).toList();
    }

    @GetMapping("/overview")
    List<PositionView> overview() {
        return cash.overview().stream().map(PositionView::of).toList();
    }
}
