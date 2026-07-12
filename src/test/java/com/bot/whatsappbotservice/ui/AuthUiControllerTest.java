package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.auth.AuthService;
import com.bot.whatsappbotservice.auth.PasswordResetRequestOutcome;
import com.bot.whatsappbotservice.auth.PasswordResetRequestResult;
import com.bot.whatsappbotservice.auth.PasswordResetService;
import com.bot.whatsappbotservice.auth.dto.AuthResponse;
import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AuthUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private PasswordResetService passwordResetService;

    // UiModelAttributesAdvice (@ControllerAdvice over all of com.bot.whatsappbotservice.ui) needs
    // this on every UI slice test; without it the whole context fails to load.
    @MockitoBean
    private TenantRepository tenantRepository;

    // SecurityConfig's *other* filter chain (for /api/**) is still part of this slice's
    // configuration graph even though this test never touches it. Mocking these three directly
    // (rather than their transitive dependencies) sidesteps that whole dependency chain.
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @Test
    void loginPageRendersWithoutAuthentication() throws Exception {
        MvcTestResult result = mvc.get().uri("/ui/login").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Vendor Login");
    }

    @Test
    void registerPageRendersForm() throws Exception {
        // CSRF auto-injection is verified separately in SecurityConfigUiPartitionTest, which runs
        // with real security filters enabled — disabled here (addFilters=false) so there's no
        // CsrfFilter populating a token to inject in the first place.
        MvcTestResult result = mvc.get().uri("/ui/register").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Register your store");
        assertThat(body).contains("name=\"tenantName\"");
    }

    @Test
    void submittingValidRegistrationRedirectsToLogin() {
        when(authService.register(ArgumentMatchers.any())).thenReturn(
                new AuthResponse("access", "refresh", "Bearer", 900));

        MvcTestResult result = mvc.post().uri("/ui/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("tenantName", "Acme Dairy")
                .param("slug", "acme-dairy")
                .param("adminFullName", "Alice Admin")
                .param("adminEmail", "alice@acme.test")
                .param("adminPassword", "supersecret1")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/login");
    }

    @Test
    void submittingInvalidRegistrationReRendersFormWithErrors() throws Exception {
        MvcTestResult result = mvc.post().uri("/ui/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("tenantName", "")
                .param("slug", "Not A Valid Slug!")
                .param("adminFullName", "")
                .param("adminEmail", "not-an-email")
                .param("adminPassword", "short")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void forgotPasswordPageRendersWithoutAuthentication() throws Exception {
        MvcTestResult result = mvc.get().uri("/ui/forgot-password").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("name=\"email\"");
    }

    @Test
    void submittingForgotPasswordRedirectsToResetPageOnSuccess() {
        when(passwordResetService.requestReset("alice@acme.test"))
                .thenReturn(PasswordResetRequestResult.of(PasswordResetRequestOutcome.CODE_SENT));

        MvcTestResult result = mvc.post().uri("/ui/forgot-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/reset-password");
    }

    @Test
    void onScreenCodeIsFlashedToTheResetPage() {
        when(passwordResetService.requestReset("alice@acme.test"))
                .thenReturn(new PasswordResetRequestResult(PasswordResetRequestOutcome.CODE_SENT, "654321"));

        MvcTestResult result = mvc.post().uri("/ui/forgot-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/reset-password");
        assertThat(result.getMvcResult().getFlashMap().get("onScreenCode")).isEqualTo("654321");
    }

    @Test
    void unknownAccountGetsTheSameNeutralRedirectAsASentCode() {
        when(passwordResetService.requestReset("nobody@acme.test"))
                .thenReturn(PasswordResetRequestResult.of(PasswordResetRequestOutcome.UNKNOWN_ACCOUNT));

        MvcTestResult result = mvc.post().uri("/ui/forgot-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "nobody@acme.test")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/reset-password");
    }

    @Test
    void forgotPasswordCooldownReRendersFormWithError() throws Exception {
        doThrow(new BusinessRuleViolationException("A code was already sent recently."))
                .when(passwordResetService).requestReset("alice@acme.test");

        MvcTestResult result = mvc.post().uri("/ui/forgot-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("already sent recently");
    }

    @Test
    void missingDeliveryChannelReRendersFormWithHonestError() throws Exception {
        when(passwordResetService.requestReset("alice@acme.test"))
                .thenReturn(PasswordResetRequestResult.of(PasswordResetRequestOutcome.NO_DELIVERY_CHANNEL));

        MvcTestResult result = mvc.post().uri("/ui/forgot-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No WhatsApp number is configured");
    }

    @Test
    void submittingValidResetRedirectsToLogin() {
        MvcTestResult result = mvc.post().uri("/ui/reset-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .param("code", "654321")
                .param("newPassword", "newpassword1")
                .param("confirmPassword", "newpassword1")
                .exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/login");
    }

    @Test
    void mismatchedPasswordsReRenderResetFormWithError() throws Exception {
        MvcTestResult result = mvc.post().uri("/ui/reset-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .param("code", "654321")
                .param("newPassword", "newpassword1")
                .param("confirmPassword", "different1")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Passwords do not match");
    }

    @Test
    void invalidCodeReRendersResetFormWithError() throws Exception {
        doThrow(new BusinessRuleViolationException("Incorrect code. Please try again."))
                .when(passwordResetService).resetPassword("alice@acme.test", "000000", "newpassword1");

        MvcTestResult result = mvc.post().uri("/ui/reset-password")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("email", "alice@acme.test")
                .param("code", "000000")
                .param("newPassword", "newpassword1")
                .param("confirmPassword", "newpassword1")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Incorrect code");
    }

    @Test
    void duplicateSlugReRendersFormWithError() throws Exception {
        doThrow(new DuplicateResourceException("A tenant with slug 'acme-dairy' already exists"))
                .when(authService).register(ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("tenantName", "Acme Dairy")
                .param("slug", "acme-dairy")
                .param("adminFullName", "Alice Admin")
                .param("adminEmail", "alice@acme.test")
                .param("adminPassword", "supersecret1")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("already exists");
    }
}
