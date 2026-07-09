package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * A one-time code sent to a customer's WhatsApp number to establish a storefront session, without
 * a password. Mirrors {@code security.RefreshToken}'s hash-at-rest/expiry/single-use idiom (the
 * plaintext code is never persisted, only its SHA-256 hash) but is genuinely tenant-scoped (unlike
 * {@code RefreshToken}, which is keyed to a global {@code TenantUser} id) since it's issued before
 * any authenticated session exists, purely from the {@code {tenantSlug}} in the storefront URL.
 */
@Getter
@Setter
@Entity
@Table(name = "customer_otp_code")
public class CustomerOtpCode extends TenantScopedEntity {

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

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
