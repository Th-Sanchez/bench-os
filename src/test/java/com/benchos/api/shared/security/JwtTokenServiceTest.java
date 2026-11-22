package com.benchos.api.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-length!";

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), "https://api.benchos.com");
        jwtTokenService = new JwtTokenService(encoder, decoder, properties);
    }

    @Test
    void generatesAndDecodesTokenWithExpectedClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        JwtTokenService.IssuedToken issued = jwtTokenService.generateToken(userId, tenantId, "OWNER");
        Jwt decoded = jwtTokenService.decode(issued.accessToken());

        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getClaimAsString(JwtTokenService.CLAIM_TENANT_ID)).isEqualTo(tenantId.toString());
        assertThat(decoded.getClaimAsString(JwtTokenService.CLAIM_ROLE)).isEqualTo("OWNER");
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://api.benchos.com");
        assertThat(decoded.getExpiresAt()).isNotNull();
    }

    @Test
    void rejectsTokenWithTamperedSignature() {
        JwtTokenService.IssuedToken issued = jwtTokenService.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "ATTENDANT"
        );
        String tampered = issued.accessToken() + "tampered";

        assertThatThrownBy(() -> jwtTokenService.decode(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsWeakSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtProperties("too-short", Duration.ofHours(1), "benchos-api"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }
}
