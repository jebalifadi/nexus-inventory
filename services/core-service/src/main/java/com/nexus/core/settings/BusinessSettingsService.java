package com.nexus.core.settings;

import com.nexus.core.shared.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The owner's choices for the whole business, the same on every computer. */
@Service
public class BusinessSettingsService {

    private static final BusinessSettings DEFAULTS = new BusinessSettings(null, 5, 30, 90);
    private static final int MAX_LOW_STOCK_LIMIT = 100_000;
    private static final int MAX_REMINDER_DAYS = 3_650;

    private final BusinessSettingsRepository repository;

    BusinessSettingsService(BusinessSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public BusinessSettings current() {
        return repository.find().orElse(DEFAULTS);
    }

    @Transactional
    public BusinessSettings update(String currency, Integer lowStockLimit, Integer paymentReminderDays,
                                   Integer slowStockDays) {
        if (currency == null || currency.isBlank()) {
            throw new BusinessRuleException("Choose a currency");
        }
        if (lowStockLimit == null || lowStockLimit < 0 || lowStockLimit > MAX_LOW_STOCK_LIMIT) {
            throw new BusinessRuleException("Low-stock limit must be between 0 and " + MAX_LOW_STOCK_LIMIT);
        }
        if (paymentReminderDays == null || paymentReminderDays < 1 || paymentReminderDays > MAX_REMINDER_DAYS) {
            throw new BusinessRuleException("Payment reminder must be between 1 and " + MAX_REMINDER_DAYS + " days");
        }
        if (slowStockDays == null || slowStockDays < 1 || slowStockDays > MAX_REMINDER_DAYS) {
            throw new BusinessRuleException("Slow-moving stock must be between 1 and " + MAX_REMINDER_DAYS + " days");
        }
        // An unknown currency is refused by the database, which lists the currencies Nexus supports.
        BusinessSettings settings = new BusinessSettings(currency.strip().toUpperCase(), lowStockLimit,
                paymentReminderDays, slowStockDays);
        repository.save(settings);
        return settings;
    }
}
