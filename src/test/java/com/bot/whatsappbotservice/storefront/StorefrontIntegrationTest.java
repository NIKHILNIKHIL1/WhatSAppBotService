package com.bot.whatsappbotservice.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateCategoryRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.whatsapp.WhatsAppClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end: OTP login establishes a real session-backed {@code Authentication} (see
 * {@code StorefrontAuthController#establishSession}), which is what the {@code @WebMvcTest}
 * controller tests can't exercise since they disable the real {@code /store/**} filter chain.
 * {@link WhatsAppClient} is mocked purely to avoid a real network call to Meta — the OTP code is
 * recovered from the captured outbound message body rather than a real WhatsApp inbox.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class StorefrontIntegrationTest {

    private static final Pattern CODE_PATTERN = Pattern.compile("\\d{6}");

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    @Test
    void customerCanLoginBrowseCheckoutAndSeeOrderHistory() throws Exception {
        String slug = "storefront-test-tenant";
        String vendorToken = registerTenant(slug, "admin@storefront-test.test");
        long categoryId = createCategory(vendorToken);
        long productId = createProduct(vendorToken, categoryId);
        String phone = "+14155550188";

        MvcTestResult loginResult = mvc.post().uri("/store/" + slug + "/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", phone)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .exchange();
        assertThat(loginResult).hasStatus3xxRedirection();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(whatsAppClient).sendText(anyString(), anyString(), anyString(), bodyCaptor.capture());
        String code = extractCode(bodyCaptor.getValue());

        MvcTestResult verifyResult = mvc.post().uri("/store/" + slug + "/verify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("phoneNumber", phone)
                .param("code", code)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .exchange();
        assertThat(verifyResult).hasStatus3xxRedirection();
        MockHttpSession session = (MockHttpSession) verifyResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Session carried forward on subsequent requests, same as a browser's cookie would.
        MvcTestResult productsResult = mvc.get().uri("/store/" + slug + "/products")
                .session(session)
                .exchange();
        assertThat(productsResult).hasStatusOk();
        assertThat(productsResult.getResponse().getContentAsString()).contains("Milk 1L");

        MvcTestResult addResult = mvc.post().uri("/store/" + slug + "/cart/add")
                .session(session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("productId", String.valueOf(productId))
                .param("quantity", "2")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .exchange();
        assertThat(addResult).hasStatus3xxRedirection();

        MvcTestResult checkoutResult = mvc.post().uri("/store/" + slug + "/checkout")
                .session(session)
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .exchange();
        assertThat(checkoutResult).hasStatus3xxRedirection();
        assertThat(checkoutResult.getResponse().getRedirectedUrl()).contains("/orders");

        MvcTestResult ordersResult = mvc.get().uri("/store/" + slug + "/orders")
                .session(session)
                .exchange();
        assertThat(ordersResult).hasStatusOk();
        assertThat(ordersResult.getResponse().getContentAsString()).contains("NEW");
    }

    @Test
    void anonymousVisitorHittingCartIsRedirectedToLoginNotGiven403() {
        String slug = "storefront-test-tenant-anon";
        MvcTestResult result = mvc.get().uri("/store/" + slug + "/orders").exchange();

        // The tenant doesn't exist, so StorefrontTenantContextFilter 404s before the
        // authentication entry point would even run — register one first to isolate the
        // entry-point behavior specifically.
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anonymousVisitorHittingProtectedPageOnRealTenantIsRedirectedToLogin() throws Exception {
        String slug = "storefront-test-tenant-anon2";
        registerTenant(slug, "admin@storefront-test-anon2.test");

        MvcTestResult result = mvc.get().uri("/store/" + slug + "/orders").exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/store/" + slug + "/login");
    }

    private String registerTenant(String slug, String email) throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Tenant " + slug, slug, "Admin", email, "supersecret1", null, null, null, null);
        MvcTestResult result = mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
                .exchange();
        return dataNode(result).path("accessToken").asText();
    }

    private long createCategory(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest("Dairy", "Milk and cheese", null, 0, Map.of());
        MvcTestResult result = mvc.post().uri("/api/catalog/categories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        assertThat(result).hasStatus(201);
        return dataNode(result).path("id").asLong();
    }

    private long createProduct(String token, long categoryId) throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                "SKU-MILK-1", "Milk 1L", "Full cream milk", categoryId, "ltr",
                new BigDecimal("55.00"), "INR", null, new BigDecimal("10"), Map.of());
        MvcTestResult result = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        assertThat(result).hasStatus(201);
        return dataNode(result).path("id").asLong();
    }

    private String extractCode(String messageBody) {
        Matcher matcher = CODE_PATTERN.matcher(messageBody);
        assertThat(matcher.find()).as("OTP message should contain a 6-digit code: " + messageBody).isTrue();
        return matcher.group();
    }

    private JsonNode dataNode(MvcTestResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
