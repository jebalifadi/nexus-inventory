package com.nexus.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what.
 *
 * <ul>
 *   <li>Public: login, health check, system info and the API documentation.</li>
 *   <li>VIEWER: may read everything (GET), change nothing.</li>
 *   <li>OWNER: may do everything, and is the only one who sees users and the audit log.</li>
 * </ul>
 *
 * <p>The API is stateless: no session and no cookie, every request carries a bearer token. That is
 * also why CSRF protection, which protects cookies, is not needed here.
 */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**", "/api/v1/system/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/users/**", "/api/v1/audit/**").hasRole(Role.OWNER.name())
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole(Role.OWNER.name(), Role.VIEWER.name())
                        .anyRequest().hasRole(Role.OWNER.name()))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(rolesFromToken())));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(TokenService tokens) {
        return tokens.decoder();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Turns the token's "roles" claim (["OWNER"]) into Spring's ROLE_OWNER. */
    private static JwtAuthenticationConverter rolesFromToken() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
