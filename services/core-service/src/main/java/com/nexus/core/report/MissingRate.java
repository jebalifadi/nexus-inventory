package com.nexus.core.report;

import java.time.LocalDate;

/**
 * An exchange rate a report could not find, so some amounts were left out of it.
 *
 * @param neededBy a rate dated on or before this day covers every amount that was left out
 */
public record MissingRate(String from, String to, LocalDate neededBy) {
}
