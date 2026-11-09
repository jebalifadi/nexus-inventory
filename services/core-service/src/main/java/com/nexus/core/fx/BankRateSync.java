package com.nexus.core.fx;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps the rates current from the Central Bank of Tunisia: checks every hour while Nexus runs, and
 * once per start fills in recent working days that have no rate yet. If the bank cannot be reached,
 * the rates already saved keep being used and the next check tries again.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "nexus.rates.bank-sync.enabled", havingValue = "true")
class BankRateSync {

    private static final Logger log = LoggerFactory.getLogger(BankRateSync.class);
    private static final String BASE = "https://www.bct.gov.tn/bct/siteprod/";
    private static final String LATEST = BASE + "cours.jsp";
    private static final int BACKFILL_DAYS = 45;
    private static final long PAUSE_BETWEEN_REQUESTS_MS = 500;

    private final ExchangeRateService rates;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private boolean backfilled;

    BankRateSync(ExchangeRateService rates) {
        this.rates = rates;
    }

    @Scheduled(fixedDelay = 1, timeUnit = java.util.concurrent.TimeUnit.HOURS)
    void sync() {
        try {
            save(fetch(LATEST));
            if (!backfilled) {
                backfill();
                backfilled = true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Could not update exchange rates from the Central Bank of Tunisia: {}", e.toString());
        }
    }

    private void backfill() throws InterruptedException {
        Set<LocalDate> known = rates.listRates().stream()
                .filter(r -> r.base().equals("EUR") && r.quote().equals("TND"))
                .map(ExchangeRate::rateDate)
                .collect(Collectors.toSet());
        int skipped = 0;
        for (int back = 1; back <= BACKFILL_DAYS; back++) {
            LocalDate day = LocalDate.now().minusDays(back);
            if (known.contains(day) || day.getDayOfWeek() == DayOfWeek.SATURDAY
                    || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            Thread.sleep(PAUSE_BETWEEN_REQUESTS_MS);
            try {
                save(fetch(BASE + "cours_archiv.jsp?input=" + day + "&langue=fr"));
            } catch (IOException | IllegalArgumentException e) {
                skipped++; // bank holidays have no table
            }
        }
        log.info("Exchange rate history filled in; {} day(s) had no rates", skipped);
    }

    private void save(BankRates day) {
        day.dinarsPerUnit().forEach((currency, rate) -> rates.saveBankRate(currency, "TND", rate, day.date()));
    }

    private BankRates fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Nexus-Inventory")
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + url);
        }
        return BctRateParser.parse(decode(response.body()));
    }

    /** The live page is ISO-8859-1 and the archive UTF-8. */
    private static String decode(byte[] body) {
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
        } catch (CharacterCodingException e) {
            return new String(body, StandardCharsets.ISO_8859_1);
        }
    }
}
