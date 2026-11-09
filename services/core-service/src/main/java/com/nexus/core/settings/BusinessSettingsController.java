package com.nexus.core.settings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the owner's settings. Everyone signed in can read them; only the owner can change them. */
@RestController
@RequestMapping("/api/v1/settings")
class BusinessSettingsController {

    record SettingsChange(String currency, Integer lowStockLimit, Integer paymentReminderDays) {
    }

    private final BusinessSettingsService settings;

    BusinessSettingsController(BusinessSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    BusinessSettings get() {
        return settings.current();
    }

    @PutMapping
    BusinessSettings update(@RequestBody SettingsChange change) {
        return settings.update(change.currency(), change.lowStockLimit(), change.paymentReminderDays());
    }
}
