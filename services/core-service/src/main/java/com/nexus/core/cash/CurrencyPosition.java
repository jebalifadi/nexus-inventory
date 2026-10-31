package com.nexus.core.cash;

import com.nexus.core.money.Money;

/**
 * The business in one currency.
 *
 * <p>Business value = cash + stock (at purchase cost) + receivables − payables. Receivables are what
 * customers still owe: they are never counted as cash.
 */
public record CurrencyPosition(String currency, Money cash, Money stockValue, Money receivables, Money payables,
                               Money businessValue) {
}
