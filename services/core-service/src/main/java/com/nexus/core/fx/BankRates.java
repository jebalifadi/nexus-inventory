package com.nexus.core.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One day of official rates from the Central Bank of Tunisia.
 *
 * @param dinarsPerUnit by currency code, what one unit costs in Tunisian dinars
 */
record BankRates(LocalDate date, Map<String, BigDecimal> dinarsPerUnit) {
}
