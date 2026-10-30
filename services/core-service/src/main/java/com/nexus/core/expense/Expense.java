package com.nexus.core.expense;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/**
 * A cost outside the purchase price.
 *
 * @param unitId the unit it belongs to (U-…), if any
 * @param saleId the sale it belongs to (S-…), if any; with neither, it is a general business cost
 */
public record Expense(Long id, String publicId, ExpenseCategory category, Money amount, LocalDate expenseDate,
                      String description, String unitId, String saleId) {
}
