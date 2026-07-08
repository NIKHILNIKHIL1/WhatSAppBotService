package com.bot.whatsappbotservice.security;

import com.bot.whatsappbotservice.tenant.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        if (properties.secret() == null || properties.secret().isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret must be configured (set the JWT_SECRET environment variable)");
        }
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, Long tenantId, UserRole role, String email) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role.name())
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES)))
                .signWith(signingKey);
        if (tenantId != null) {
            builder.claim("tenantId", tenantId);
        }
        return builder.compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long accessTokenExpirationSeconds() {
        return properties.accessTokenExpirationMinutes() * 60;
    }

    public long refreshTokenExpirationDays() {
        return properties.refreshTokenExpirationDays();
    }
}
