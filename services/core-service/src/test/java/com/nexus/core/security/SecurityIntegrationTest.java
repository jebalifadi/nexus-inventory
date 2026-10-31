package com.nexus.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;

/** Calls the real HTTP API: login, roles, the audit log and the public pages. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:18-alpine:///nexus",
        "spring.datasource.username=test",
        "spring.datasource.password=test"
})
class SecurityIntegrationTest {

    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private Environment environment;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void ownersChangeThingsViewersOnlyLookAndEveryChangeIsAudited() throws Exception {
        assertThat(call("GET", "/api/v1/locations", null, null).statusCode()).isEqualTo(401);

        HttpResponse<String> wrongLogin = call("POST", "/api/v1/auth/login", null,
                "{\"username\":\"owner\",\"password\":\"not-the-password\"}");
        assertThat(wrongLogin.statusCode()).isEqualTo(401);
        assertThat(wrongLogin.body()).contains("Wrong username or password");

        String owner = login("owner", "change-me-please");
        assertThat(call("POST", "/api/v1/locations", owner, "{\"name\":\"Vienna shop\",\"country\":\"Austria\"}")
                .statusCode()).isEqualTo(201);
        assertThat(call("POST", "/api/v1/users", owner,
                "{\"username\":\"accountant\",\"password\":\"viewer-password-1\",\"role\":\"VIEWER\"}")
                .statusCode()).isEqualTo(201);

        String viewer = login("accountant", "viewer-password-1");
        assertThat(call("GET", "/api/v1/locations", viewer, null).statusCode()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/locations", viewer, "{\"name\":\"Graz shop\",\"country\":\"Austria\"}")
                .statusCode()).isEqualTo(403);
        assertThat(call("GET", "/api/v1/audit", viewer, null).statusCode()).isEqualTo(403);

        HttpResponse<String> audit = call("GET", "/api/v1/audit", owner, null);
        assertThat(audit.statusCode()).isEqualTo(200);
        assertThat(audit.body()).contains("/api/v1/locations").contains("\"owner\"").contains("\"anonymous\"");
        assertThat(audit.body()).doesNotContain("change-me-please");

        // The log can only grow.
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_log").update()).isInstanceOf(DataAccessException.class);
    }

    @Test
    void healthSystemInfoAndApiDocumentationArePublic() throws Exception {
        assertThat(call("GET", "/api/v1/system/info", null, null).statusCode()).isEqualTo(200);
        assertThat(call("GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
        HttpResponse<String> docs = call("GET", "/v3/api-docs", null, null);
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("Nexus API");
    }

    private String login(String username, String password) throws IOException, InterruptedException {
        HttpResponse<String> response = call("POST", "/api/v1/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
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
