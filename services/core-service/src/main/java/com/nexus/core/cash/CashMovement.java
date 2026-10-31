package com.nexus.core.cash;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/**
 * One line of the cash book. {@code amount} is positive for money in and negative for money out.
 *
 * @param reference what it belongs to, for example a sale (S-…), a purchase (P-…) or an expense (EXP-…)
 */
public record CashMovement(LocalDate date, String type, String reference, Money amount) {
}
