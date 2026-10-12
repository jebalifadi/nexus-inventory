package com.nexus.core.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Runs the application and its Flyway migrations against a disposable PostgreSQL container. */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class SystemInfoIntegrationTest {

    @Autowired
    private SystemInfoController controller;

    @Test
    void returnsApplicationNameAndConfiguredCurrencies() {
        SystemInfo info = controller.info();

        assertThat(info.application()).isEqualTo("Nexus");
        assertThat(info.supportedCurrencies()).containsExactly("EUR", "TND");
    }
}
