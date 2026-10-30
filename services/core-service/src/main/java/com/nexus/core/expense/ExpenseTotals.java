package com.nexus.core.expense;

import com.nexus.core.money.Money;
import com.nexus.core.sale.ExtraCosts;
import java.util.List;
import org.springframework.stereotype.Component;

/** Gives sales the expenses that lower their profit. */
@Component
class ExpenseTotals implements ExtraCosts {

    private final ExpenseRepository repository;

    ExpenseTotals(ExpenseRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Money> forUnit(long unitId) {
        return repository.amountsForUnit(unitId);
    }

    @Override
    public List<Money> forSale(long saleId) {
        return repository.amountsForSale(saleId);
    }
}
