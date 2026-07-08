package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.bot.whatsappbotservice.audit.AuditService;
import com.bot.whatsappbotservice.audit.dto.AuditLogResponse;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(AuditUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuditService auditService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @Test
    @WithMockUser(roles = "VENDOR_ADMIN")
    void listRendersAuditEntries() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AuditLogResponse entry = new AuditLogResponse(1L, "Product", "42", AuditAction.UPDATE,
                objectMapper.readTree("{\"price\":\"10.00\"}"), objectMapper.readTree("{\"price\":\"12.00\"}"),
                "alice@example.com", "127.0.0.1", AuditChannel.API, Instant.now());
        when(auditService.list(any(), any())).thenReturn(new PageImpl<>(List.of(entry)));

        MvcTestResult result = mvc.get().uri("/ui/audit").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Product #42").contains("alice@example.com");
    }

    @Test
    @WithMockUser(roles = "VENDOR_ADMIN")
    void listShowsEmptyState() throws Exception {
        when(auditService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/audit").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No audit entries yet");
    }
}
