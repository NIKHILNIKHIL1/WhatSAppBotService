package com.bot.whatsappbotservice.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class CustomerOtpServiceTest {

    private static final String PHONE = "+14155550100";

    @Mock
    private CustomerOtpCodeRepository otpCodeRepository;
    @Mock
    private CustomerService customerService;
    @Mock
    private WhatsAppMessagingService messagingService;
    @Mock
    private WhatsAppMessages messages;

    private CustomerOtpService otpService;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        otpService = new CustomerOtpService(otpCodeRepository, customerService, messagingService, messages);

        tenant = new Tenant();
        tenant.setId(1L);
        tenant.setDefaultLanguageCode("en");

        when(messages.get(eq("otp.code.message"), eq("en"), any())).thenReturn("Your code is 123456");
        when(otpCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void requestOtpSendsCodeWhenNoRecentCodeExists() {
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());
        when(messagingService.sendText(eq(tenant), isNull(), eq(PHONE), any())).thenReturn(MessageStatus.SENT);

        MessageStatus status = otpService.requestOtp(tenant, PHONE);

        assertThat(status).isEqualTo(MessageStatus.SENT);
        verify(otpCodeRepository).save(any(CustomerOtpCode.class));
    }

    @Test
    void requestOtpPropagatesFailedDeliveryStatus() {
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());
        when(messagingService.sendText(eq(tenant), isNull(), eq(PHONE), any())).thenReturn(MessageStatus.FAILED);

        MessageStatus status = otpService.requestOtp(tenant, PHONE);

        assertThat(status).isEqualTo(MessageStatus.FAILED);
    }

    @Test
    void requestOtpRejectsWhenAnUnexpiredCodeWasJustSent() {
        CustomerOtpCode recent = new CustomerOtpCode();
        recent.setExpiresAt(Instant.now().plusSeconds(240));
        recent.setCreatedAt(Instant.now());
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> otpService.requestOtp(tenant, PHONE))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(messagingService, never()).sendText(any(), any(), any(), any());
    }

    @Test
    void verifyFailsWhenNoCodeExists() {
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> otpService.verify(PHONE, "123456"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyFailsWhenCodeExpired() {
        CustomerOtpCode expired = new CustomerOtpCode();
        expired.setExpiresAt(Instant.now().minusSeconds(10));
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> otpService.verify(PHONE, "123456"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void verifyIncrementsAttemptCountOnWrongCode() {
        CustomerOtpCode stored = validStoredCode("999999");
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> otpService.verify(PHONE, "000000"))
                .isInstanceOf(BusinessRuleViolationException.class);

        assertThat(stored.getAttemptCount()).isEqualTo(1);
        verify(otpCodeRepository).save(stored);
        verify(customerService, never()).findOrCreateByPhoneNumber(any(), any(), any());
    }

    @Test
    void verifyLocksOutAfterMaxAttempts() {
        CustomerOtpCode stored = validStoredCode("999999");
        stored.setAttemptCount(5);
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> otpService.verify(PHONE, "999999"))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(customerService, never()).findOrCreateByPhoneNumber(any(), any(), any());
    }

    @Test
    void verifySucceedsAndResolvesCustomerOnCorrectCode() {
        CustomerOtpCode stored = validStoredCode("654321");
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(stored));
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setPhoneNumber(PHONE);
        customer.setStatus(CustomerStatus.ACTIVE);
        when(customerService.findOrCreateByPhoneNumber(PHONE, null, null)).thenReturn(customer);

        Customer result = otpService.verify(PHONE, "654321");

        assertThat(result).isSameAs(customer);
        assertThat(stored.getConsumedAt()).isNotNull();
    }

    @Test
    void verifyRejectsBlockedCustomer() {
        CustomerOtpCode stored = validStoredCode("654321");
        when(otpCodeRepository.findFirstByPhoneNumberOrderByCreatedAtDesc(PHONE)).thenReturn(Optional.of(stored));
        Customer customer = new Customer();
        customer.setId(9L);
        customer.setStatus(CustomerStatus.BLOCKED);
        when(customerService.findOrCreateByPhoneNumber(PHONE, null, null)).thenReturn(customer);

        assertThatThrownBy(() -> otpService.verify(PHONE, "654321"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private CustomerOtpCode validStoredCode(String plainCode) {
        CustomerOtpCode code = new CustomerOtpCode();
        code.setExpiresAt(Instant.now().plusSeconds(240));
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
