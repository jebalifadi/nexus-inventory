package com.nexus.core.cash;

import com.nexus.core.money.Money;
import java.time.LocalDate;

/** Money changed from one currency into another, at the rate the owner actually got. */
public record CurrencyExchange(Long id, String publicId, Money from, Money to, LocalDate date, String notes) {
}
