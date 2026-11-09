package com.nexus.core.fx;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the interbank average rates from a Central Bank of Tunisia page. The page also lists rates
 * for cash exchange, which are not used. Anything unexpected is refused rather than stored: a wrong
 * rate would silently change the figures, while a missing one is reported on the dashboard.
 */
final class BctRateParser {

    private static final Pattern DAY = Pattern.compile("Journ.{1,2}e du (\\d{2}/\\d{2}/\\d{4})");
    private static final Pattern ROW = Pattern.compile("\\b(EUR|USD)\\s+(\\d+)\\s+(\\d+,\\d+)");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final BigDecimal LOWEST_PLAUSIBLE = new BigDecimal("0.5");
    private static final BigDecimal HIGHEST_PLAUSIBLE = new BigDecimal("50");

    private BctRateParser() {
    }

    static BankRates parse(String html) {
        String text = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ");
        Matcher first = DAY.matcher(text);
        if (!first.find()) {
            throw new IllegalArgumentException("No rate table on the page");
        }
        String heading = text.substring(Math.max(0, first.start() - 200), first.start());
        if (!heading.contains("interbancaire") || heading.contains("Change Manuel")) {
            throw new IllegalArgumentException("The first table is not the interbank one");
        }
        int tableEnd = first.find(first.end()) ? first.start() : text.length();
        first.find(0);
        String table = text.substring(first.end(), tableEnd);

        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        Matcher row = ROW.matcher(table);
        while (row.find()) {
            BigDecimal value = new BigDecimal(row.group(3).replace(',', '.'));
            BigDecimal perUnit = value.divide(new BigDecimal(row.group(2)), MathContext.DECIMAL64);
            if (perUnit.compareTo(LOWEST_PLAUSIBLE) < 0 || perUnit.compareTo(HIGHEST_PLAUSIBLE) > 0) {
                throw new IllegalArgumentException("Implausible rate for " + row.group(1) + ": " + perUnit);
            }
            rates.put(row.group(1), perUnit);
        }
        if (rates.isEmpty()) {
            throw new IllegalArgumentException("No EUR or USD rate in the table");
        }
        return new BankRates(LocalDate.parse(first.group(1), DATE), rates);
    }
}
