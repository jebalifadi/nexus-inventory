package com.nexus.core.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The API documentation page at /swagger-ui.html, with a button to paste a login token. */
@Configuration
class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI nexusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nexus API")
                        .version("v1")
                        .description("Inventory management for trading companies: catalog, stock, purchases, "
                                + "shipping, sales, payments, warranty and cash. Log in with POST /api/v1/auth/login "
                                + "and use the token with the Authorize button."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
