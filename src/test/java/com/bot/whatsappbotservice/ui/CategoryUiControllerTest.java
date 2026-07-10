package com.bot.whatsappbotservice.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.config.RateLimitingFilter;
import com.bot.whatsappbotservice.config.RequestIdFilter;
import com.bot.whatsappbotservice.security.JwtService;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(CategoryUiController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryUiControllerTest {

    @Autowired
    private MockMvcTester mvc;

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

    @BeforeEach
    void setUp() {
        when(tenantService.getCurrent()).thenReturn(new TenantProfileResponse(
                1L, "Tenant", "tenant", null, null, false, null, "en", "INR", "UTC", "ACTIVE", "META", null, false,
                List.of("en"), true));
    }

    @Test
    void listRendersCategoriesTable() throws Exception {
        CategoryResponse dairy = new CategoryResponse(1L, "Dairy", "Milk & cheese", null, 0, true, java.util.Map.of());
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of(dairy)));

        MvcTestResult result = mvc.get().uri("/ui/categories").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("Dairy").contains("Milk &amp; cheese");
    }

    @Test
    void listShowsEmptyStateWhenNoCategories() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/categories").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("No categories yet");
    }

    @Test
    void newFormRenders() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/categories/new").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("New Category");
    }

    @Test
    void editFormPrefillsExistingValues() throws Exception {
        CategoryResponse dairy = new CategoryResponse(1L, "Dairy", "Milk & cheese", null, 2, true, java.util.Map.of());
        when(categoryService.get(1L)).thenReturn(dairy);
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.get().uri("/ui/categories/1/edit").exchange();

        assertThat(result).hasStatusOk();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("Edit Category");
        assertThat(body).contains("value=\"Dairy\"");
    }

    @Test
    void creatingWithBlankNameReRendersFormWithError() throws Exception {
        when(categoryService.list(any())).thenReturn(new PageImpl<>(List.of()));

        MvcTestResult result = mvc.post().uri("/ui/categories/new")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", "")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result.getResponse().getContentAsString()).contains("field-error");
    }

    @Test
    void deactivateRedirectsToList() {
        MvcTestResult result = mvc.post().uri("/ui/categories/1/deactivate").exchange();

        assertThat(result).hasStatus3xxRedirection();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/ui/categories");
    }
}
