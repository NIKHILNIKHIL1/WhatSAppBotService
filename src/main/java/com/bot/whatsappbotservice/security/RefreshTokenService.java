package com.bot.whatsappbotservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public String issue(Long tenantUserId) {
        String plaintext = generateOpaqueToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTenantUserId(tenantUserId);
        refreshToken.setTokenHash(hash(plaintext));
        refreshToken.setExpiresAt(Instant.now().plus(jwtProperties.refreshTokenExpirationDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refreshToken);
        return plaintext;
    }

    /**
     * Validates the presented refresh token and revokes it (rotation) so it cannot be replayed.
     * Callers are responsible for issuing a replacement token on success.
     */
    @Transactional
    public Long validateAndRevoke(String plaintext) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(plaintext))
                .filter(RefreshToken::isValid)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);
        return stored.getTenantUserId();
    }

    /** Revokes every live session for a user — used after a password reset, when the old
     * credential must stop working everywhere, not just in the browser that changed it. Loops
     * entity-by-entity rather than a bulk JPQL update so {@code @Version} stays honest. */
    @Transactional
    public void revokeAllForUser(Long tenantUserId) {
        Instant now = Instant.now();
        var activeTokens = refreshTokenRepository.findAllByTenantUserIdAndRevokedAtIsNull(tenantUserId);
        activeTokens.forEach(rt -> rt.setRevokedAt(now));
        refreshTokenRepository.saveAll(activeTokens);
    }

    @Transactional
    public void revoke(String plaintext) {
        refreshTokenRepository.findByTokenHash(hash(plaintext)).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            refreshTokenRepository.save(rt);
        });
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
