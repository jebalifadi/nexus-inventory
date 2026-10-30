package com.nexus.core.expense;

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

/** REST API for expenses. */
@RestController
@RequestMapping("/api/v1/expenses")
class ExpenseController {

    record NewExpense(ExpenseCategory category, MoneyJson amount, LocalDate expenseDate, String description,
                      String unitId, String saleId) {
    }

    record ExpenseView(String id, ExpenseCategory category, MoneyJson amount, LocalDate expenseDate,
                       String description, String unitId, String saleId) {

        static ExpenseView of(Expense e) {
            return new ExpenseView(e.publicId(), e.category(), MoneyJson.of(e.amount()), e.expenseDate(),
                    e.description(), e.unitId(), e.saleId());
        }
    }

    private final ExpenseService expenses;

    ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExpenseView add(@RequestBody NewExpense request) {
        if (request.amount() == null) {
            throw new BusinessRuleException("amount is required");
        }
        return ExpenseView.of(expenses.addExpense(request.category(), request.amount().toMoney(),
                request.expenseDate(), request.description(), request.unitId(), request.saleId()));
    }

    @GetMapping
    List<ExpenseView> list() {
        return expenses.listExpenses().stream().map(ExpenseView::of).toList();
    }
}
