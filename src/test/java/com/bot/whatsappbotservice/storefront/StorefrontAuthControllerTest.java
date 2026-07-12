package com.bot.whatsappbotservice.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Covers form-validation/error-path rendering only (no DB, no real session establishment) — the
 * authenticated session-establishment path itself is exercised end-to-end by the Testcontainers
 * {@code StorefrontIntegrationTest}, since it depends on the real {@code /store/**} filter chain
 * that {@code @AutoConfigureMockMvc(addFilters = false)} disables here.
 */
@WebMvcTest(StorefrontAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class StorefrontAuthControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CustomerOtpService otpService;
    @MockitoBean
    private SecurityContextRepository storefrontSecurityContextRepository;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    // UiModelAttributesAdvice (@ControllerAdvice over all of com.bot.whatsappbotservice.ui) needs
    // this on every UI slice test; without it the whole context fails to load.
    @MockitoBean
    private TenantRepository tenantRepository;

    @Test
    void loginFormRenders() throws Exception {
        MvcTestResult result = mvc.get().uri("/store/acme/login").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Log in");
    }

    @Test
    void blankPhoneNumberReRendersFormWithError() throws Exception {
        MvcTestResult result = mvc.post().uri("/store/acme/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void validPhoneNumberRedirectsToVerifyOnSuccessfulSend() throws Exception {
        when(otpService.requestOtp(any(), any())).thenReturn(MessageStatus.SENT);

        MvcTestResult result = mvc.post().uri("/store/acme/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).contains("/store/acme/verify");
    }

    @Test
    void failedDeliveryReRendersLoginWithError() throws Exception {
        when(otpService.requestOtp(any(), any())).thenReturn(MessageStatus.FAILED);

        MvcTestResult result = mvc.post().uri("/store/acme/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void cooldownRejectionReRendersLoginWithError() throws Exception {
        when(otpService.requestOtp(any(), any()))
                .thenThrow(new BusinessRuleViolationException("A code was already sent recently."));

        MvcTestResult result = mvc.post().uri("/store/acme/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void verifyFormRenders() throws Exception {
        MvcTestResult result = mvc.get().uri("/store/acme/verify").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Enter your code");
    }

    @Test
    void incorrectCodeReRendersVerifyWithError() throws Exception {
        when(otpService.verify(any(), any())).thenThrow(new BusinessRuleViolationException("Incorrect code."));

        MvcTestResult result = mvc.post().uri("/store/acme/verify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", "+14155550100")
                .param("code", "000000")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    private Tenant sampleTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setDefaultLanguageCode("en");
        return tenant;
    }
}
