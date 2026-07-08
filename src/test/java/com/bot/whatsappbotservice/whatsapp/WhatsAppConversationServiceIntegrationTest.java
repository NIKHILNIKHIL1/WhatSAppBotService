package com.bot.whatsappbotservice.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.bot.whatsappbotservice.auth.dto.RegisterRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateCategoryRequest;
import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerRepository;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Regression test for the fetch-type/transactional design in {@link WhatsAppConversationService}:
 * {@code Product}/{@code Category.translations} are LAZY, relying on {@code @Transactional} on
 * {@link WhatsAppConversationService#handleMessage} to keep the whole turn in one persistence
 * context. A pure Mockito unit test (see {@link WhatsAppConversationServiceTest}) can't catch a
 * regression here — mocked repositories never produce a real Hibernate proxy, so a
 * {@code LazyInitializationException} would silently not happen in that suite. This test uses a
 * real Postgres (via Testcontainers) and the real Spring-wired bean graph specifically to close
 * that gap.
 *
 * <p>{@link WhatsAppClient} is mocked purely to avoid real network calls to Meta — the
 * conversation flow logs the outbound message payload (what we assert on) before attempting the
 * send, so mocking the transport doesn't weaken what this test verifies.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class WhatsAppConversationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private WhatsAppConversationService conversationService;
    @Autowired
    private WhatsAppMessageRepository whatsAppMessageRepository;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    @Test
    void categoryAndProductNamesAreLocalizedWhenCustomerPicksFrenchWithoutLazyInitializationException()
            throws Exception {
        String slug = "lang-test-tenant";
        String token = registerTenantSupportingEnglishAndFrench(slug, "admin@lang-test.test");
        Tenant tenant = tenantRepository.findBySlug(slug).orElseThrow();

        long categoryId = createCategoryWithFrenchTranslation(token);
        createProductWithFrenchTranslation(token, categoryId);

        Customer customer = createCustomer(tenant, "+14155550199");

        TenantContext.setTenantId(tenant.getId());
        try {
            // "hi" is a reset trigger: restarts at LANGUAGE_SELECTION and sends the language prompt.
            conversationService.handleMessage(tenant, customer, "wamid-1", "hi", null);
            // Tenant supports {en, fr} in that fixed enum order, so "2" selects French and advances
            // to CATEGORY_SELECTION, which is where the LazyInitializationException risk lives for
            // Category.translations.
            conversationService.handleMessage(tenant, customer, "wamid-2", "2", null);
            // No native replyId here (this is the numeric-fallback path Twilio uses); "1" resolves
            // against the category list just sent, advancing to PRODUCT_SELECTION — exercising the
            // same risk for Product.translations.
            conversationService.handleMessage(tenant, customer, "wamid-3", "1", null);

            var listMessages = whatsAppMessageRepository.findAll().stream()
                    .filter(m -> "interactive_list".equals(m.getMessageType()))
                    .toList();
            assertThat(listMessages).hasSizeGreaterThanOrEqualTo(2);
            assertThat(listMessages.get(0).getPayload()).contains("Produits Laitiers");
            assertThat(listMessages.get(1).getPayload()).contains("Lait 1L");
        } finally {
            TenantContext.clear();
        }
    }

    private String registerTenantSupportingEnglishAndFrench(String slug, String email) throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Tenant " + slug, slug, "Admin", email, "supersecret1", null, null, "en", Set.of("en", "fr"));
        MvcTestResult result = mvc.post().uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(register))
                .exchange();
        return dataNode(result).path("accessToken").asText();
    }

    private long createCategoryWithFrenchTranslation(String token) throws Exception {
        CreateCategoryRequest request = new CreateCategoryRequest(
                "Dairy Products", "Milk and cheese", null, 0,
                Map.of("fr", new TranslationDto("Produits Laitiers", "Lait et fromage")));
        MvcTestResult result = mvc.post().uri("/api/catalog/categories")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        assertThat(result).hasStatus(201);
        return dataNode(result).path("id").asLong();
    }

    private void createProductWithFrenchTranslation(String token, long categoryId) throws Exception {
        CreateProductRequest request = new CreateProductRequest(
                "SKU-MILK-FR", "Milk 1L", "Full cream milk", categoryId, "ltr",
                new BigDecimal("55.00"), "INR", null, new BigDecimal("10"),
                Map.of("fr", new TranslationDto("Lait 1L", "Lait entier")));
        MvcTestResult result = mvc.post().uri("/api/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .exchange();
        assertThat(result).hasStatus(201);
    }

    private Customer createCustomer(Tenant tenant, String phoneNumber) {
        TenantContext.setTenantId(tenant.getId());
        try {
            Customer customer = new Customer();
            customer.setPhoneNumber(phoneNumber);
            return customerRepository.save(customer);
        } finally {
            TenantContext.clear();
        }
    }

    private JsonNode dataNode(MvcTestResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
