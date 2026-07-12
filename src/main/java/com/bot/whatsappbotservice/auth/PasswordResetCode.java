package com.bot.whatsappbotservice.auth;

import com.bot.whatsappbotservice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A one-time code sent over WhatsApp that lets a vendor reset their dashboard password. Mirrors
 * {@code storefront.CustomerOtpCode}'s hash-at-rest/expiry/single-use idiom (the plaintext code is
 * never persisted, only its SHA-256 hash), but like {@code security.RefreshToken} it is
 * deliberately NOT tenant-scoped: it's keyed to a global {@code TenantUser} id, and the reset
 * happens before any authenticated session (and therefore any tenant) exists.
 */
@Getter
@Setter
@Entity
@Table(name = "password_reset_code")
public class PasswordResetCode extends BaseEntity {

    @Column(name = "tenant_user_id", nullable = false)
    private Long tenantUserId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    public boolean isValid() {
        return consumedAt == null && expiresAt.isAfter(Instant.now());
    }
}
