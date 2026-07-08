package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(TenantSettingsUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class TenantSettingsUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private TenantService tenantService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    private TenantProfileResponse sampleProfile(boolean tokenConfigured) {
        return new TenantProfileResponse(1L, "Acme Dairy", "acme-dairy", "PHONE_ID", "WABA_ID",
                tokenConfigured, "+19998887777", "en", "INR", "UTC", "ACTIVE", "META", null, false,
                java.util.List.of("en"));
    }

    @Test
    @WithMockUser(roles = "VENDOR_ADMIN")
    void viewRendersTenantProfile() throws Exception {
        when(tenantService.getCurrent()).thenReturn(sampleProfile(true));

        MvcTestResult result = mvc.get().uri("/ui/settings").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Acme Dairy").contains("Configured");
    }

    @Test
    @WithMockUser(roles = "VENDOR_ADMIN")
    void updatingWithBlankAccessTokenReRendersFormWithError() throws Exception {
        when(tenantService.getCurrent()).thenReturn(sampleProfile(false));

        MvcTestResult result = mvc.post().uri("/ui/settings/whatsapp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("whatsappPhoneNumberId", "PHONE_ID")
                .param("whatsappAccessToken", "")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    @WithMockUser(roles = "VENDOR_ADMIN")
    void duplicatePhoneNumberIdReRendersFormWithError() throws Exception {
        when(tenantService.getCurrent()).thenReturn(sampleProfile(true));
        doThrow(new DuplicateResourceException("WhatsApp phone number id 'PHONE_ID' is already linked to another tenant"))
                .when(tenantService).updateWhatsAppConfig(ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/settings/whatsapp")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("whatsappPhoneNumberId", "PHONE_ID")
                .param("whatsappAccessToken", "secret-token")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("already linked");
    }
}
