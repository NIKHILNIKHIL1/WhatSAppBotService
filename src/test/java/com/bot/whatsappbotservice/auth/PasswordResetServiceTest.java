package com.bot.whatsappbotservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetServiceTest {

    private static final String EMAIL = "alice@acme.test";
    private static final String VENDOR_PHONE = "+14155550100";

    @Mock
    private PasswordResetCodeRepository resetCodeRepository;
    @Mock
    private TenantUserRepository tenantUserRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private WhatsAppMessagingService messagingService;
    @Mock
    private WhatsAppMessages messages;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordResetService resetService;
    private Tenant tenant;
    private TenantUser user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resetService = new PasswordResetService(resetCodeRepository, tenantUserRepository, tenantRepository,
                messagingService, messages, passwordEncoder, refreshTokenService, false);

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setDefaultLanguageCode("en");
        tenant.setVendorNotificationPhoneNumber(VENDOR_PHONE);

        user = new TenantUser();
        user.setId(7L);
        user.setEmail(EMAIL);
        user.setStatus(UserStatus.ACTIVE);
        user.setTenant(tenant);
        user.setPasswordHash("old-hash");

        when(tenantUserRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
        when(messages.get(eq("password.reset.code.message"), eq("en"), any())).thenReturn("Your code is 123456");
        when(resetCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestResetSendsCodeToVendorPhoneAndClearsTenantContext() {
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        when(messagingService.sendText(eq(tenant), isNull(), eq(VENDOR_PHONE), any()))
                .thenReturn(MessageStatus.SENT);

        PasswordResetRequestResult result = resetService.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.CODE_SENT);
        assertThat(result.onScreenCode()).isNull();
        verify(resetCodeRepository).save(any(PasswordResetCode.class));
        assertThat(TenantContext.getTenantId()).isNull();
    }

    @Test
    void requestResetReportsUnknownAccountWithoutSending() {
        when(tenantUserRepository.findByEmailIgnoreCase("nobody@acme.test")).thenReturn(Optional.empty());

        PasswordResetRequestResult result = resetService.requestReset("nobody@acme.test");

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.UNKNOWN_ACCOUNT);
        verify(messagingService, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void requestResetReportsMissingDeliveryChannelWhenNoVendorPhoneConfigured() {
        tenant.setVendorNotificationPhoneNumber(null);

        PasswordResetRequestResult result = resetService.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.NO_DELIVERY_CHANNEL);
        verify(messagingService, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void requestResetReportsMissingDeliveryChannelForTenantlessSuperAdmin() {
        user.setTenant(null);

        PasswordResetRequestResult result = resetService.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.NO_DELIVERY_CHANNEL);
    }

    @Test
    void onScreenModeReturnsPlainCodeWithoutTouchingWhatsApp() {
        PasswordResetService onScreen = new PasswordResetService(resetCodeRepository, tenantUserRepository,
                tenantRepository, messagingService, messages, passwordEncoder, refreshTokenService, true);
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());

        PasswordResetRequestResult result = onScreen.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.CODE_SENT);
        assertThat(result.onScreenCode()).matches("\\d{6}");
        verify(messagingService, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void onScreenModeWorksWithoutAnyWhatsAppConfiguration() {
        PasswordResetService onScreen = new PasswordResetService(resetCodeRepository, tenantUserRepository,
                tenantRepository, messagingService, messages, passwordEncoder, refreshTokenService, true);
        user.setTenant(null);
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());

        PasswordResetRequestResult result = onScreen.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.CODE_SENT);
        assertThat(result.onScreenCode()).isNotNull();
    }

    @Test
    void onScreenModeStillEnforcesCooldown() {
        PasswordResetService onScreen = new PasswordResetService(resetCodeRepository, tenantUserRepository,
                tenantRepository, messagingService, messages, passwordEncoder, refreshTokenService, true);
        PasswordResetCode recent = new PasswordResetCode();
        recent.setExpiresAt(Instant.now().plusSeconds(540));
        recent.setCreatedAt(Instant.now());
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> onScreen.requestReset(EMAIL))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void onScreenCodeRoundTripsThroughResetPassword() {
        PasswordResetService onScreen = new PasswordResetService(resetCodeRepository, tenantUserRepository,
                tenantRepository, messagingService, messages, passwordEncoder, refreshTokenService, true);
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        PasswordResetRequestResult result = onScreen.requestReset(EMAIL);

        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(resetCodeRepository).save(saved.capture());
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L))
                .thenReturn(Optional.of(saved.getValue()));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        onScreen.resetPassword(EMAIL, result.onScreenCode(), "newpassword1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(refreshTokenService).revokeAllForUser(7L);
    }

    @Test
    void requestResetRejectsWhenACodeWasJustSent() {
        PasswordResetCode recent = new PasswordResetCode();
        recent.setExpiresAt(Instant.now().plusSeconds(540));
        recent.setCreatedAt(Instant.now());
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> resetService.requestReset(EMAIL))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(messagingService, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void requestResetPropagatesFailedDelivery() {
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.empty());
        when(messagingService.sendText(eq(tenant), isNull(), eq(VENDOR_PHONE), any()))
                .thenReturn(MessageStatus.FAILED);

        PasswordResetRequestResult result = resetService.requestReset(EMAIL);

        assertThat(result.outcome()).isEqualTo(PasswordResetRequestOutcome.SEND_FAILED);
    }

    @Test
    void resetPasswordUpdatesHashConsumesCodeAndRevokesAllSessions() {
        PasswordResetCode stored = validStoredCode("654321");
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(stored));
        when(passwordEncoder.encode("newpassword1")).thenReturn("new-hash");

        resetService.resetPassword(EMAIL, "654321", "newpassword1");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(stored.getConsumedAt()).isNotNull();
        verify(tenantUserRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(7L);
    }

    @Test
    void resetPasswordIncrementsAttemptCountOnWrongCode() {
        PasswordResetCode stored = validStoredCode("654321");
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> resetService.resetPassword(EMAIL, "000000", "newpassword1"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(stored.getAttemptCount()).isEqualTo(1);
        verify(resetCodeRepository).save(stored);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    void resetPasswordLocksOutAfterMaxAttemptsEvenWithCorrectCode() {
        PasswordResetCode stored = validStoredCode("654321");
        stored.setAttemptCount(5);
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> resetService.resetPassword(EMAIL, "654321", "newpassword1"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void resetPasswordFailsWhenCodeExpired() {
        PasswordResetCode expired = new PasswordResetCode();
        expired.setExpiresAt(Instant.now().minusSeconds(10));
        when(resetCodeRepository.findFirstByTenantUserIdOrderByCreatedAtDesc(7L)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> resetService.resetPassword(EMAIL, "654321", "newpassword1"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void resetPasswordFailsForUnknownEmailWithSameErrorAsMissingCode() {
        when(tenantUserRepository.findByEmailIgnoreCase("nobody@acme.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resetService.resetPassword("nobody@acme.test", "654321", "newpassword1"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("expired or not found");
    }

    private PasswordResetCode validStoredCode(String plainCode) {
        PasswordResetCode code = new PasswordResetCode();
        code.setTenantUserId(7L);
        code.setExpiresAt(Instant.now().plusSeconds(540));
        code.setCodeHash(hash(plainCode));
        return code;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
