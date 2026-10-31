package com.nexus.core.cash;

import com.nexus.core.money.Money;

/**
 * What the owner put in and took out, per currency. Kept apart from profit: putting money in is
 * not earning it.
 */
public record CapitalSummary(String currency, Money added, Money withdrawn, Money netContribution,
                             Money profitWithdrawn) {
}
