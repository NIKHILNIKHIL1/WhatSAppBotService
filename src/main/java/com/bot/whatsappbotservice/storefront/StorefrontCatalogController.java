package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public (anonymous-browsable) catalog pages for the customer storefront. */
@Controller
@RequestMapping("/store/{slug}")
public class StorefrontCatalogController {

    private static final int MAX_ROWS = 100;

    private final CategoryService categoryService;
    private final ProductService productService;

    public StorefrontCatalogController(CategoryService categoryService, ProductService productService) {
        this.categoryService = categoryService;
        this.productService = productService;
    }

    @GetMapping
    public String categories(@RequestParam(required = false) String lang, HttpServletRequest request,
                              @AuthenticationPrincipal CustomerPrincipal customer, Model model) {
        String resolvedLang = resolveLanguage(lang, customer, request);
        List<CategoryResponse> categories = categoryService.list(PageRequest.of(0, MAX_ROWS)).getContent();
        model.addAttribute("categories", categories);
        model.addAttribute("lang", resolvedLang);
        model.addAttribute("localization", new CategoryLocalizer(resolvedLang));
        return "store/catalog";
    }

    @GetMapping("/products")
    public String products(@RequestParam(required = false) Long categoryId, @RequestParam(required = false) String lang,
                            HttpServletRequest request, @AuthenticationPrincipal CustomerPrincipal customer,
                            Model model) {
        String resolvedLang = resolveLanguage(lang, customer, request);
        List<ProductResponse> products = productService.list(categoryId, PageRequest.of(0, MAX_ROWS)).getContent();
        model.addAttribute("products", products);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("lang", resolvedLang);
        model.addAttribute("localization", new ProductLocalizer(resolvedLang));
        return "store/products";
    }

    private String resolveLanguage(String queryLang, CustomerPrincipal customer, HttpServletRequest request) {
        if (StringUtils.hasText(queryLang)) {
            return queryLang;
        }
        if (customer != null && StringUtils.hasText(customer.getPreferredLanguageCode())) {
            return customer.getPreferredLanguageCode();
        }
        Tenant tenant = (Tenant) request.getAttribute(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE);
        return tenant.getDefaultLanguageCode();
    }

    /** Thin, template-friendly wrappers so Thymeleaf can call {@code ${localization.name(product)}}
     * without re-passing the language code at every call site. */
    public record ProductLocalizer(String languageCode) {
        public String name(ProductResponse product) {
            return StorefrontLocalization.productName(product, languageCode);
        }

        public String description(ProductResponse product) {
            return StorefrontLocalization.productDescription(product, languageCode);
        }
    }

    public record CategoryLocalizer(String languageCode) {
        public String name(CategoryResponse category) {
            return StorefrontLocalization.categoryName(category, languageCode);
        }

        public String description(CategoryResponse category) {
            return StorefrontLocalization.categoryDescription(category, languageCode);
        }
    }
}
