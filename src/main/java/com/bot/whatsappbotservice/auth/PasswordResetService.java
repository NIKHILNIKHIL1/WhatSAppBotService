package com.bot.whatsappbotservice.auth;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.security.RefreshTokenService;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.tenant.TenantUser;
import com.bot.whatsappbotservice.tenant.TenantUserRepository;
import com.bot.whatsappbotservice.tenant.UserStatus;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessagingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Password reset for vendor dashboard accounts, delivered as a one-time code over WhatsApp to the
 * tenant's {@code vendorNotificationPhoneNumber} — this stack has no email transport, and WhatsApp
 * is already the trusted side channel for the storefront's OTP login. Mirrors
 * {@code storefront.CustomerOtpService}'s hash-at-rest/cooldown/attempt-cap idiom.
 *
 * <p>With {@code app.password-reset.on-screen-delivery=true} the WhatsApp send is skipped and the
 * plaintext code is returned for the UI to display — a development convenience that removes the
 * flow's entire security value (see the comment at that branch); never enable it in production.
 *
 * <p>{@link #requestReset} is deliberately NOT {@code @Transactional}: the request arrives
 * unauthenticated, so no filter has populated {@link TenantContext}, yet the outbound send logs a
 * tenant-scoped {@code WhatsAppMessage}. The context is set around the send and cleared in a
 * {@code finally}; the send commits its own transaction inside that window — a method-level
 * transaction would defer that flush until after the context is already cleared.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final PasswordResetCodeRepository resetCodeRepository;
    private final TenantUserRepository tenantUserRepository;
    private final TenantRepository tenantRepository;
    private final WhatsAppMessagingService messagingService;
    private final WhatsAppMessages messages;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final boolean onScreenDelivery;

    public PasswordResetService(PasswordResetCodeRepository resetCodeRepository,
                                 TenantUserRepository tenantUserRepository, TenantRepository tenantRepository,
                                 WhatsAppMessagingService messagingService, WhatsAppMessages messages,
                                 PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService,
                                 @Value("${app.password-reset.on-screen-delivery:false}") boolean onScreenDelivery) {
        this.resetCodeRepository = resetCodeRepository;
        this.tenantUserRepository = tenantUserRepository;
        this.tenantRepository = tenantRepository;
        this.messagingService = messagingService;
        this.messages = messages;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.onScreenDelivery = onScreenDelivery;
    }

    /** Templates use this to word the flow correctly for the active delivery mode. */
    public boolean isOnScreenDelivery() {
        return onScreenDelivery;
    }

    /**
     * Sends a fresh reset code over WhatsApp. Throws {@link BusinessRuleViolationException} when a
     * code was already sent within the cooldown window; every other condition is reported as an
     * outcome so the controller can decide what the (possibly unauthenticated, possibly hostile)
     * requester gets to see.
     */
    public PasswordResetRequestResult requestReset(String email) {
        TenantUser user = tenantUserRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown or inactive account");
            return PasswordResetRequestResult.of(PasswordResetRequestOutcome.UNKNOWN_ACCOUNT);
        }

        if (onScreenDelivery) {
            enforceCooldown(user.getId());
            // DEV-MODE PATH: the plaintext code is handed back to whichever browser asked for it,
            // so possession of the code proves nothing about the requester — anyone who knows a
            // vendor's email can take over the account while this flag is on. Needs no WhatsApp
            // config, which also makes it the only reset path that works for a tenantless
            // SUPER_ADMIN.
            return new PasswordResetRequestResult(PasswordResetRequestOutcome.CODE_SENT,
                    createAndStoreCode(user.getId()));
        }

        // getTenant() is a detached lazy proxy here (no surrounding transaction); only its id is
        // safe to touch, so the full Tenant is re-fetched — same pattern as AuthService.issueTokens.
        Tenant tenant = user.getTenant() != null
                ? tenantRepository.findById(user.getTenant().getId()).orElse(null)
                : null;
        if (tenant == null || !StringUtils.hasText(tenant.getVendorNotificationPhoneNumber())) {
            log.info("Password reset for user {} has no WhatsApp delivery channel", user.getId());
            return PasswordResetRequestResult.of(PasswordResetRequestOutcome.NO_DELIVERY_CHANNEL);
        }

        enforceCooldown(user.getId());
        String plainCode = createAndStoreCode(user.getId());

        String body = messages.get("password.reset.code.message", tenant.getDefaultLanguageCode(), plainCode);
        try {
            TenantContext.setTenantId(tenant.getId());
            MessageStatus status = messagingService.sendText(tenant, null,
                    tenant.getVendorNotificationPhoneNumber(), body);
            return PasswordResetRequestResult.of(status == MessageStatus.SENT
                    ? PasswordResetRequestOutcome.CODE_SENT
                    : PasswordResetRequestOutcome.SEND_FAILED);
        } finally {
            TenantContext.clear();
        }
    }

    private void enforceCooldown(Long tenantUserId) {
        resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(tenantUserId)
                .filter(code -> code.isValid() && code.getCreatedAt().isAfter(Instant.now().minus(REQUEST_COOLDOWN)))
                .ifPresent(code -> {
                    throw new BusinessRuleViolationException(
                            "A code was already sent recently. Please wait a moment before requesting another.");
                });
    }

    private String createAndStoreCode(Long tenantUserId) {
        String plainCode = generateCode();
        PasswordResetCode resetCode = new PasswordResetCode();
        resetCode.setTenantUserId(tenantUserId);
        resetCode.setCodeHash(hash(plainCode));
        resetCode.setExpiresAt(Instant.now().plus(CODE_TTL));
        resetCodeRepository.save(resetCode);
        return plainCode;
    }

    /**
     * Validates the code and sets the new password. {@code noRollbackFor} keeps the wrong-code
     * attempt increment: without it, the very exception that reports "incorrect code" would roll
     * back the increment it just recorded, making {@code MAX_ATTEMPTS} unenforceable.
     */
    @Transactional(noRollbackFor = BusinessRuleViolationException.class)
    public void resetPassword(String email, String code, String newPassword) {
        TenantUser user = tenantUserRepository.findByEmailIgnoreCase(email)
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(PasswordResetService::invalidCode);
        PasswordResetCode resetCode = resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(user.getId())
                .filter(PasswordResetCode::isValid)
                .orElseThrow(PasswordResetService::invalidCode);

        if (resetCode.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessRuleViolationException("Too many incorrect attempts. Please request a new code.");
        }
        if (!resetCode.getCodeHash().equals(hash(code))) {
            resetCode.setAttemptCount(resetCode.getAttemptCount() + 1);
            resetCodeRepository.save(resetCode);
            throw new BusinessRuleViolationException("Incorrect code. Please try again.");
        }

        resetCode.setConsumedAt(Instant.now());
        resetCodeRepository.save(resetCode);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tenantUserRepository.save(user);
        // The old credential may be compromised — kill every live session, not just the current one.
        refreshTokenService.revokeAllForUser(user.getId());
    }

    private static BusinessRuleViolationException invalidCode() {
        return new BusinessRuleViolationException("Code expired or not found. Please request a new one.");
    }

    private static String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
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
