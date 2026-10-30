package com.nexus.core.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for exchange rates. */
@RestController
@RequestMapping("/api/v1/exchange-rates")
class ExchangeRateController {

    record NewRate(String base, String quote, BigDecimal rate, LocalDate rateDate) {
    }

    private final ExchangeRateService rates;

    ExchangeRateController(ExchangeRateService rates) {
        this.rates = rates;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ExchangeRate add(@RequestBody NewRate request) {
        return rates.addRate(request.base(), request.quote(), request.rate(), request.rateDate());
    }

    @GetMapping
    List<ExchangeRate> list() {
        return rates.listRates();
    }
}
