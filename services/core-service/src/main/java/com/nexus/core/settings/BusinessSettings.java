package com.nexus.core.settings;

/**
 * What the owner chooses for the whole business.
 *
 * @param currency            the currency reports are shown in; null until the owner has chosen one
 * @param lowStockLimit       a product with this many pieces or fewer counts as low on stock
 * @param paymentReminderDays money owed for longer than this counts as overdue
 * @param slowStockDays       goods in stock for longer than this count as slow-moving
 */
public record BusinessSettings(String currency, int lowStockLimit, int paymentReminderDays, int slowStockDays) {
}
