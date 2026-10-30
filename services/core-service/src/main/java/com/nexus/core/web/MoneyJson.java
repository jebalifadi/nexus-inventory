package com.nexus.core.web;

import com.nexus.core.money.Money;
import com.nexus.core.shared.BusinessRuleException;

/** How money looks in the API: the amount as text (never a rounded float) and the currency code. */
public record MoneyJson(String amount, String currency) {

    public static MoneyJson of(Money money) {
        return money == null ? null
                : new MoneyJson(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }

    public Money toMoney() {
        if (amount == null || currency == null) {
            throw new BusinessRuleException("Amount and currency are required");
        }
        try {
            return Money.of(amount, currency);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid amount or currency: " + amount + " " + currency);
        }
    }
}
