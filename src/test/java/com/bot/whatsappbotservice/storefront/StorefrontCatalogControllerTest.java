package com.bot.whatsappbotservice.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Anonymous, unauthenticated catalog browsing — no login required, matches {@code SecurityConfig}'s
 * {@code permitAll()} rules for these paths. */
@WebMvcTest(StorefrontCatalogController.class)
@AutoConfigureMockMvc(addFilters = false)
class StorefrontCatalogControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;

    @Test
    void categoriesPageRendersLocalizedNameWhenTranslationExists() throws Exception {
        CategoryResponse dairy = new CategoryResponse(1L, "Dairy", "Milk & cheese", null, 0, true,
                Map.of("fr", new com.bot.whatsappbotservice.i18n.TranslationDto("Laitier", null)));
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of(dairy)));

        MvcTestResult result = mvc.get().uri("/store/acme?lang=fr")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Laitier");
    }

    @Test
    void categoriesPageFallsBackToBaseNameWhenNoTranslation() throws Exception {
        CategoryResponse dairy = new CategoryResponse(1L, "Dairy", "Milk & cheese", null, 0, true, Map.of());
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of(dairy)));

        MvcTestResult result = mvc.get().uri("/store/acme")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Dairy");
    }

    @Test
    void emptyProductListShowsEmptyState() throws Exception {
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/store/acme/products")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No products available");
    }

    @Test
    void productsPageRendersPriceAndAddToCartForm() throws Exception {
        ProductResponse milk = new ProductResponse(2L, "SKU-1", "Milk 1L", null, null, "ltr",
                new BigDecimal("55.00"), "INR", null, true, Map.of());
        when(productService.list(any(), any())).thenReturn(new PageImpl<>(List.of(milk)));

        MvcTestResult result = mvc.get().uri("/store/acme/products")
                .requestAttr(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE, sampleTenant())
                .exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Milk 1L").contains("Add to cart");
    }

    private Tenant sampleTenant() {
        Tenant tenant = new Tenant();
        tenant.setId(1L);
        tenant.setDefaultLanguageCode("en");
        return tenant;
    }
}
