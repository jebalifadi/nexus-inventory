package com.nexus.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;

/** Calls the catalog over real HTTP, to check how request bodies are read. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class CatalogApiIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Test
    void fieldFlagsMayBeLeftOut() throws Exception {
        String owner = login();
        // No "required" and no "inName": both mean no, and every product field then forms the name.
        HttpResponse<String> category = call("POST", "/api/v1/categories", owner, """
                {"name":"Bags","trackingMode":"LOT","fields":[
                  {"label":"Brand","type":"TEXT","scope":"PRODUCT"},
                  {"label":"Model","type":"TEXT","scope":"PRODUCT"}]}""");
        assertThat(category.statusCode()).isEqualTo(201);
        assertThat(category.body()).contains("\"required\":false").contains("\"namePosition\":0");

        HttpResponse<String> marked = call("POST", "/api/v1/categories", owner, """
                {"name":"Belts","trackingMode":"LOT","fields":[
                  {"label":"Brand","type":"TEXT","scope":"PRODUCT","required":true},
                  {"label":"Length","type":"TEXT","scope":"PRODUCT","inName":true}]}""");
        assertThat(marked.statusCode()).isEqualTo(201);
        assertThat(marked.body()).contains("\"namePosition\":null").contains("\"namePosition\":0");
    }

    private String login() throws IOException, InterruptedException {
        HttpResponse<String> response = call("POST", "/api/v1/auth/login", null,
                "{\"username\":\"owner\",\"password\":\"change-me-please\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        Matcher matcher = TOKEN.matcher(response.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private HttpResponse<String> call(String method, String path, String token, String json)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
