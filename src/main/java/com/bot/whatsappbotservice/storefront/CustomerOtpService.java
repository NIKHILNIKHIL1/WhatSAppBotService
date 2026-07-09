package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessagingService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phone+OTP login for the customer storefront — no password, code delivered over WhatsApp via the
 * same {@link WhatsAppMessagingService} the bot uses. Mirrors {@code security.RefreshTokenService}'s
 * hash-at-rest/single-use idiom (the plaintext code is never persisted), but issued per phone
 * number rather than per authenticated principal, since there's no session yet at request time.
 */
@Service
public class CustomerOtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration REQUEST_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final CustomerOtpCodeRepository otpCodeRepository;
    private final CustomerService customerService;
    private final WhatsAppMessagingService messagingService;
    private final WhatsAppMessages messages;

    public CustomerOtpService(CustomerOtpCodeRepository otpCodeRepository, CustomerService customerService,
                               WhatsAppMessagingService messagingService, WhatsAppMessages messages) {
        this.otpCodeRepository = otpCodeRepository;
        this.customerService = customerService;
        this.messagingService = messagingService;
        this.messages = messages;
    }

    /**
     * Sends a fresh code over WhatsApp. Returns the resulting {@link MessageStatus} — callers MUST
     * check it (SENT vs FAILED): {@link WhatsAppMessagingService} swallows transport failures
     * internally (logs + records a FAILED audit row) rather than throwing, so a caller that ignores
     * the return value would silently advance a customer to the "enter code" screen for a code that
     * never arrived.
     */
    @Transactional
    public MessageStatus requestOtp(Tenant tenant, String phoneNumber) {
        otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(code -> code.isValid() && code.getCreatedAt().isAfter(Instant.now().minus(REQUEST_COOLDOWN)))
                .ifPresent(code -> {
                    throw new BusinessRuleViolationException(
                            "A code was already sent recently. Please wait a moment before requesting another.");
                });

        String plainCode = generateCode();
        CustomerOtpCode otpCode = new CustomerOtpCode();
        otpCode.setPhoneNumber(phoneNumber);
        otpCode.setCodeHash(hash(plainCode));
        otpCode.setExpiresAt(Instant.now().plus(OTP_TTL));
        otpCodeRepository.save(otpCode);

        String body = messages.get("otp.code.message", tenant.getDefaultLanguageCode(), plainCode);
        return messagingService.sendText(tenant, null, phoneNumber, body);
    }

    /**
     * Validates the code, marks it consumed, and resolves the {@link Customer} — creating one on
     * first-ever contact (mirrors the WhatsApp bot's own first-contact behavior via
     * {@link CustomerService#findOrCreateByPhoneNumber}) so identity is unified whether a customer
     * arrives via WhatsApp or the web.
     */
    @Transactional
    public Customer verify(String phoneNumber, String code) {
        CustomerOtpCode otpCode = otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(phoneNumber)
                .filter(CustomerOtpCode::isValid)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Code expired or not found. Please request a new one."));

        if (otpCode.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new BusinessRuleViolationException("Too many incorrect attempts. Please request a new code.");
        }

        if (!otpCode.getCodeHash().equals(hash(code))) {
            otpCode.setAttemptCount(otpCode.getAttemptCount() + 1);
            otpCodeRepository.save(otpCode);
            throw new BusinessRuleViolationException("Incorrect code. Please try again.");
        }

        otpCode.setConsumedAt(Instant.now());
        otpCodeRepository.save(otpCode);

        Customer customer = customerService.findOrCreateByPhoneNumber(phoneNumber, null, null);
        if (customer.getStatus() == CustomerStatus.BLOCKED) {
            throw new BusinessRuleViolationException(
                    "This account is unable to place orders. Please contact the store.");
        }
        return customer;
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
