package com.nexus.core.expense;

import com.nexus.core.money.Money;
import com.nexus.core.sale.SaleService;
import com.nexus.core.shared.BusinessRuleException;
import com.nexus.core.stock.StockService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of expenses. An expense on a unit (repair, parts) adds to that unit's cost; an expense
 * on a sale (fees) lowers that sale's profit; an expense on nothing is a general business cost.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository repository;
    private final StockService stock;
    private final SaleService sales;

    ExpenseService(ExpenseRepository repository, StockService stock, SaleService sales) {
        this.repository = repository;
        this.stock = stock;
        this.sales = sales;
    }

    @Transactional
    public Expense addExpense(ExpenseCategory category, Money amount, LocalDate expenseDate, String description,
                              String unitId, String saleId) {
        if (category == null) {
            throw new BusinessRuleException("Category is required");
        }
        if (amount == null || amount.amount().signum() <= 0) {
            throw new BusinessRuleException("Amount must be more than zero");
        }
        boolean hasUnit = unitId != null && !unitId.isBlank();
        boolean hasSale = saleId != null && !saleId.isBlank();
        if (hasUnit && hasSale) {
            throw new BusinessRuleException("An expense belongs to a unit or to a sale, not both");
        }
        Long unit = hasUnit ? stock.getUnit(unitId.strip()).id() : null;
        Long sale = hasSale ? sales.getSale(saleId.strip()).id() : null;
        long id = repository.insert(category, amount.amount(), amount.currency().getCurrencyCode(),
                expenseDate == null ? LocalDate.now() : expenseDate,
                description == null || description.isBlank() ? null : description.strip(), unit, sale);
        return repository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<Expense> listExpenses() {
        return repository.findAll();
    }
}
