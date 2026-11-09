package com.nexus.core.report;

import com.nexus.core.money.Money;
import java.util.List;

/** Revenue and profit month by month, worked out exactly like the dashboard's figures. */
public record MonthlyTrend(String currency, List<Month> months, List<MissingRate> missingRates) {

    public MonthlyTrend {
        months = List.copyOf(months);
        missingRates = List.copyOf(missingRates);
    }

    /** @param month as YYYY-MM */
    public record Month(String month, Money revenue, Money profit, int salesCount, int unknownProfitCount) {
    }
}
