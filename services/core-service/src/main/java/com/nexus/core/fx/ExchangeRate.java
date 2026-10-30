package com.nexus.core.fx;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 1 {@code base} = {@code rate} {@code quote} on {@code rateDate}. Example: 1 EUR = 3.3798 TND. */
public record ExchangeRate(Long id, String base, String quote, BigDecimal rate, LocalDate rateDate, String source) {
}
