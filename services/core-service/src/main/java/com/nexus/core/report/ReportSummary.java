package com.nexus.core.report;

import com.nexus.core.money.Money;
import java.math.BigDecimal;
import java.util.List;

/**
 * The business figures for one period, all converted into one currency.
 *
 * @param currency           the currency of every amount
 * @param hasActivity        whether anything was ever recorded, also outside the period
 * @param invested           goods bought plus shipping paid
 * @param revenue            sales minus returns
 * @param expenses           every expense of the period
 * @param losses             real cost of goods written off
 * @param costs              expenses plus losses
 * @param profit             profit of the sales, minus general expenses, minus losses
 * @param marginPercent      profit as a percentage of revenue; null without revenue
 * @param unknownProfitCount sales left out of the profit because an exchange rate is missing
 * @param missingRates       rates still needed: without them the amounts above are incomplete
 */
public record ReportSummary(String currency, boolean hasActivity, Money boughtFor, Money shipping, Money invested,
                            Money revenue, Money expenses, Money losses, Money costs, Money profit,
                            BigDecimal marginPercent, int salesCount, int unknownProfitCount,
                            List<MissingRate> missingRates) {

    public ReportSummary {
        missingRates = List.copyOf(missingRates);
    }
}
