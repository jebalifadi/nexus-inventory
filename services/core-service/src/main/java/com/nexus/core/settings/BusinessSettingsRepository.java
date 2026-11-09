package com.nexus.core.settings;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Reads and writes the single settings row with plain SQL. */
@Repository
class BusinessSettingsRepository {

    private final JdbcClient jdbc;

    BusinessSettingsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<BusinessSettings> find() {
        return jdbc.sql("SELECT currency, low_stock_limit, payment_reminder_days FROM business_settings")
                .query((rs, n) -> {
                    String currency = rs.getString("currency");
                    return new BusinessSettings(currency == null ? null : currency.trim(),
                            rs.getInt("low_stock_limit"), rs.getInt("payment_reminder_days"));
                })
                .optional();
    }

    void save(BusinessSettings settings) {
        jdbc.sql("""
                        INSERT INTO business_settings (id, currency, low_stock_limit, payment_reminder_days)
                        VALUES (1, :currency, :lowStock, :reminderDays)
                        ON CONFLICT (id) DO UPDATE SET currency = EXCLUDED.currency,
                            low_stock_limit = EXCLUDED.low_stock_limit,
                            payment_reminder_days = EXCLUDED.payment_reminder_days,
                            updated_at = now()
                        """)
                .param("currency", settings.currency())
                .param("lowStock", settings.lowStockLimit())
                .param("reminderDays", settings.paymentReminderDays())
                .update();
    }
}
