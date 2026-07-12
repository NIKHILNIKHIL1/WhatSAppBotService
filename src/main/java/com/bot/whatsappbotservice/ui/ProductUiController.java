package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.catalog.ProductService;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.ui.form.ProductForm;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/products")
public class ProductUiController {

    private final ProductService productService;
    private final CategoryService categoryService;
    private final TenantService tenantService;

    public ProductUiController(ProductService productService, CategoryService categoryService,
                                TenantService tenantService) {
        this.productService = productService;
        this.categoryService = categoryService;
        this.tenantService = tenantService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) Long categoryId, Model model, Pageable pageable) {
        // Management view: includes deactivated products, which still own their SKU and would
        // otherwise be invisible-but-blocking ("A product with SKU ... already exists").
        model.addAttribute("products", productService.listForManagement(categoryId, pageable));
        model.addAttribute("categoryId", categoryId);
        return "ui/products/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("productForm")) {
            model.addAttribute("productForm", new ProductForm());
        }
        populateFormModel(model, false, null);
        return "ui/products/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("productForm") ProductForm form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, false, null);
            return "ui/products/form";
        }
        try {
            productService.create(form.toCreateRequest());
        } catch (DuplicateResourceException | ResourceNotFoundException e) {
            bindingResult.reject("error", e.getMessage());
            populateFormModel(model, false, null);
            return "ui/products/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Product created.");
        return "redirect:/ui/products";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("productForm")) {
            ProductResponse product = productService.get(id);
            ProductForm form = new ProductForm();
            form.setSku(product.sku());
            form.setName(product.name());
            form.setDescription(product.description());
            form.setCategoryId(product.categoryId());
            form.setUnit(product.unit());
            form.setPrice(product.price());
            form.setCurrencyCode(product.currencyCode());
            form.setImageUrl(product.imageUrl());
            form.setActive(product.active());
            if (product.translations() != null) {
                product.translations().forEach((code, translation) -> {
                    form.getNameTranslations().put(code, translation.name());
                    form.getDescriptionTranslations().put(code, translation.description());
                });
            }
            model.addAttribute("productForm", form);
        }
        populateFormModel(model, true, id);
        return "ui/products/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("productForm") ProductForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, true, id);
            return "ui/products/form";
        }
        try {
            productService.update(id, form.toUpdateRequest());
        } catch (ResourceNotFoundException e) {
            bindingResult.reject("error", e.getMessage());
            populateFormModel(model, true, id);
            return "ui/products/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Product updated.");
        return "redirect:/ui/products";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Product deactivated.");
        return "redirect:/ui/products";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        productService.reactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Product reactivated.");
        return "redirect:/ui/products";
    }

    private void populateFormModel(Model model, boolean isEdit, Long productId) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("formAction", isEdit ? "/ui/products/" + productId + "/edit" : "/ui/products/new");
        model.addAttribute("categories", categoryService.list(PageRequest.of(0, 200)).getContent());
        model.addAttribute("nonDefaultSupportedLanguages", nonDefaultSupportedLanguages());
    }

    private List<String> nonDefaultSupportedLanguages() {
        var tenant = tenantService.getCurrent();
        return SupportedLanguage.orderedCodes(Set.copyOf(tenant.supportedLanguageCodes())).stream()
                .filter(code -> !code.equals(tenant.defaultLanguageCode()))
                .toList();
    }
}
