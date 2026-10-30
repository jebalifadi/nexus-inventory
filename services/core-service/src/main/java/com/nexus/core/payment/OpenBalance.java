package com.nexus.core.payment;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/**
 * A sale someone still owes you money for (receivable), or a purchase you still owe a supplier
 * for (payable).
 */
public record OpenBalance(String reference, String counterparty, LocalDate date, long daysOpen, Money total,
                          Money paid, Money balance) {
}
