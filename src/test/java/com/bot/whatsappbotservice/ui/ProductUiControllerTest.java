package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.mockito.Mockito.doThrow;

@WebMvcTest(ProductUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService productService;
    @MockitoBean
    private CategoryService categoryService;
    @MockitoBean
    private JwtService jwtService;
    @MockitoBean
    private RequestIdFilter requestIdFilter;
    @MockitoBean
    private RateLimitingFilter rateLimitingFilter;
    @MockitoBean
    private TenantService tenantService;

    // UiModelAttributesAdvice (@ControllerAdvice over all of com.bot.whatsappbotservice.ui) needs
    // this on every UI slice test; without it the whole context fails to load.
    @MockitoBean
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        when(tenantService.getCurrent()).thenReturn(new TenantProfileResponse(
                1L, "Tenant", "tenant", null, null, false, null, "en", "INR", "UTC", "ACTIVE", "META", null, false,
                List.of("en"), true));
    }

    @Test
    void listRendersProductsTable() throws Exception {
        ProductResponse milk = new ProductResponse(1L, "SKU-1", "Milk 1L", "desc", null, "ltr",
                new BigDecimal("55.00"), "INR", null, true, java.util.Map.of());
        when(productService.listForManagement(any(), any())).thenReturn(new PageImpl<>(List.of(milk)));

        MvcTestResult result = mvc.get().uri("/ui/products").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("SKU-1").contains("Milk 1L");
    }

    @Test
    void inactiveProductShowsReactivateButtonInsteadOfDeactivate() throws Exception {
        ProductResponse retired = new ProductResponse(2L, "CRM-MLK", "Cream Milk", "desc", null, "ltr",
                new BigDecimal("60.00"), "INR", null, false, java.util.Map.of());
        when(productService.listForManagement(any(), any())).thenReturn(new PageImpl<>(List.of(retired)));

        MvcTestResult result = mvc.get().uri("/ui/products").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Inactive").contains("Reactivate");
        assertThat(body).doesNotContain(">Deactivate<");
    }

    @Test
    void reactivateRedirectsToList() {
        MvcTestResult result = mvc.post().uri("/ui/products/2/reactivate").exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/products");
    }

    @Test
    void newFormRendersWithCategoryOptions() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/products/new").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("New Product");
    }

    @Test
    void editFormMarksSkuReadonly() throws Exception {
        ProductResponse milk = new ProductResponse(1L, "SKU-1", "Milk 1L", "desc", null, "ltr",
                new BigDecimal("55.00"), "INR", null, true, java.util.Map.of());
        when(productService.get(1L)).thenReturn(milk);
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/products/1/edit").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Edit Product");
        assertThat(body).contains("readonly");
    }

    @Test
    void creatingDuplicateSkuReRendersFormWithError() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));
        doThrow(new DuplicateResourceException("A product with SKU 'SKU-1' already exists"))
                .when(productService).create(ArgumentMatchers.any());

        MvcTestResult result = mvc.post().uri("/ui/products/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sku", "SKU-1")
                .param("name", "Milk 1L")
                .param("unit", "ltr")
                .param("price", "55.00")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("already exists");
    }

    @Test
    void deactivateRedirectsToList() {
        MvcTestResult result = mvc.post().uri("/ui/products/1/deactivate").exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/products");
    }
}
