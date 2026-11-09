package com.nexus.core.fx;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Rates from the bank never replace what the owner entered. Old dates: the test classes share one database. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class BankRateIntegrationTest {

    @Autowired
    private ExchangeRateService rates;

    @Test
    void savingTheSameDayTwiceKeepsTheFirstRate() {
        LocalDate day = LocalDate.of(2016, 1, 4);
        rates.saveBankRate("EUR", "USD", new BigDecimal("1.10"), day);
        rates.saveBankRate("EUR", "USD", new BigDecimal("9.99"), day);

        assertThat(rates.rateOn("EUR", "USD", day)).get().satisfies(r -> assertThat(r).isEqualByComparingTo("1.10"));
    }

    @Test
    void whatTheOwnerEntersReplacesTheBankAndIsNotReplacedByIt() {
        LocalDate day = LocalDate.of(2016, 2, 1);
        rates.saveBankRate("EUR", "USD", new BigDecimal("1.10"), day);

        ExchangeRate manual = rates.addRate("EUR", "USD", new BigDecimal("1.25"), day);
        rates.saveBankRate("EUR", "USD", new BigDecimal("1.30"), day);

        assertThat(manual.source()).isEqualTo("MANUAL");
        assertThat(rates.rateOn("EUR", "USD", day)).get().satisfies(r -> assertThat(r).isEqualByComparingTo("1.25"));
    }

    @Test
    void aRateEnteredTheOtherWayRoundAlsoStaysInCharge() {
        LocalDate day = LocalDate.of(2016, 3, 1);
        rates.addRate("USD", "EUR", new BigDecimal("0.50"), day);
        rates.saveBankRate("EUR", "USD", new BigDecimal("1.10"), day);

        assertThat(rates.rateOn("EUR", "USD", day)).get().satisfies(r -> assertThat(r).isEqualByComparingTo("2"));
    }
}
