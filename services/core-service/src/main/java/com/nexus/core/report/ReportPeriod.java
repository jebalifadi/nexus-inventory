package com.nexus.core.report;

import com.nexus.core.shared.BusinessRuleException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/** A range of days, both included; a null end means no limit on that side. */
record ReportPeriod(LocalDate from, LocalDate to) {

    ReportPeriod {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("The period must not start after it ends");
        }
    }

    <T> List<T> keep(List<T> items, Function<T, LocalDate> date) {
        return items.stream().filter(item -> contains(date.apply(item))).toList();
    }

    boolean contains(LocalDate day) {
        return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
    }
}
