package com.nexus.core.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * Issues and checks JWT access tokens, signed with a secret key (HS256).
 *
 * <p>A token says who the user is and their role, and expires after a while. The server keeps no
 * session: every request carries its token.
 */
@Component
public class TokenService {

    static final String ROLES_CLAIM = "roles";
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration lifetime;

    TokenService(@Value("${nexus.security.jwt-secret}") String secret,
                 @Value("${nexus.security.token-lifetime:PT8H}") Duration lifetime) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("nexus.security.jwt-secret must be at least 32 bytes long");
        }
        SecretKey key = new SecretKeySpec(bytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.lifetime = lifetime;
    }

    public record Token(String value, Instant expiresAt) {
    }

    Token issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(lifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("nexus")
                .subject(user.username())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLES_CLAIM, List.of(user.role().name()))
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(value, expiresAt);
    }

    JwtDecoder decoder() {
        return decoder;
    }
}
