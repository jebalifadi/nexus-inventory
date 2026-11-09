package com.nexus.core.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexus.core.shared.BusinessRuleException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/** The owner's settings: defaults until saved, one row, and nothing out of range. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class BusinessSettingsIntegrationTest {

    @Autowired
    private BusinessSettingsService settings;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Environment environment;

    @BeforeEach
    @AfterEach
    void backToDefaults() {
        jdbc.sql("DELETE FROM business_settings").update();
    }

    @Test
    void usesTheDefaultsUntilTheOwnerSaves() {
        assertThat(settings.current()).isEqualTo(new BusinessSettings(null, 5, 30));
    }

    @Test
    void savingTwiceKeepsOneRowWithTheLatestChoice() {
        settings.update("tnd", 3, 45);
        settings.update("EUR", 0, 1);

        assertThat(settings.current()).isEqualTo(new BusinessSettings("EUR", 0, 1));
        assertThat(jdbc.sql("SELECT count(*) FROM business_settings").query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void refusesValuesThatMakeNoSense() {
        assertThatThrownBy(() -> settings.update(" ", 5, 30)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> settings.update("EUR", -1, 30)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> settings.update("EUR", null, 30)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> settings.update("EUR", 5, 0)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> settings.update("GBP", 5, 30)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(settings.current().currency()).isNull();
    }

    @Test
    void changingThemNeedsALogin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/api/v1/settings"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        "{\"currency\":\"EUR\",\"lowStockLimit\":5,\"paymentReminderDays\":30}"))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
    }
}
