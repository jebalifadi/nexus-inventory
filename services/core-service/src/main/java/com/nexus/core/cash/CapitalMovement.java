package com.nexus.core.cash;

import com.nexus.core.money.Money;
import java.time.LocalDate;

public record CapitalMovement(Long id, String publicId, CapitalType type, Money amount, LocalDate date,
                              String notes) {
}
