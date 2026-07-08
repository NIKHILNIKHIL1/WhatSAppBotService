package com.bot.whatsappbotservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bot.whatsappbotservice.tenant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final JwtProperties properties =
            new JwtProperties("unit-test-secret-key-must-be-at-least-32-bytes-long", 15, 7);
    private final JwtService jwtService = new JwtService(properties);

    @Test
    void generatesTokenWithExpectedClaims() {
        String token = jwtService.generateAccessToken(42L, 7L, UserRole.VENDOR_ADMIN, "admin@example.com");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("tenantId", Long.class)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("VENDOR_ADMIN");
        assertThat(claims.get("email", String.class)).isEqualTo("admin@example.com");
    }

    @Test
    void omitsTenantClaimForSuperAdmin() {
        String token = jwtService.generateAccessToken(1L, null, UserRole.SUPER_ADMIN, "root@platform.io");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.get("tenantId")).isNull();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(1L, 1L, UserRole.VENDOR_STAFF, "staff@example.com");
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> jwtService.parseClaims(tampered)).isInstanceOf(SignatureException.class);
    }

    @Test
    void constructorRejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("", 15, 7)))
                .isInstanceOf(IllegalStateException.class);
    }
}
