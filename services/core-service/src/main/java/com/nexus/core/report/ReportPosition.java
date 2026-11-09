package com.nexus.core.report;

import com.nexus.core.money.Money;
import java.time.LocalDate;
import java.util.List;

/**
 * Where the business stands today, converted into one currency at today's rate.
 *
 * @param stockValue what the goods in stock cost
 * @param owedToYou  what customers still owe
 * @param youOwe     what is still owed to suppliers
 */
public record ReportPosition(String currency, Money stockValue, OpenBalances owedToYou, OpenBalances youOwe,
                             List<MissingRate> missingRates) {

    public ReportPosition {
        missingRates = List.copyOf(missingRates);
    }

    /** @param total the rows that could be converted, added up */
    public record OpenBalances(Money total, List<Row> rows) {

        public OpenBalances {
            rows = List.copyOf(rows);
        }
    }

    /**
     * @param balance   what is open, in the currency of the sale or purchase
     * @param converted the same amount in the report's currency; null when no rate is known
     * @param overdue   open for longer than the owner's payment reminder
     */
    public record Row(String reference, String counterparty, LocalDate date, long daysOpen, Money balance,
                      Money converted, boolean overdue) {
    }
}
