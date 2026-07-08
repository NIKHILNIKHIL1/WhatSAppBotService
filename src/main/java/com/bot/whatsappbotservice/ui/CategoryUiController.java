package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.catalog.CategoryService;
import com.bot.whatsappbotservice.common.exception.ResourceNotFoundException;
import com.bot.whatsappbotservice.i18n.SupportedLanguage;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.ui.form.CategoryForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/categories")
public class CategoryUiController {

    private final CategoryService categoryService;
    private final TenantService tenantService;

    public CategoryUiController(CategoryService categoryService, TenantService tenantService) {
        this.categoryService = categoryService;
        this.tenantService = tenantService;
    }

    @GetMapping
    public String list(Model model, Pageable pageable) {
        model.addAttribute("categories", categoryService.list(pageable));
        return "ui/categories/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("categoryForm")) {
            model.addAttribute("categoryForm", new CategoryForm());
        }
        populateFormModel(model, false, null);
        return "ui/categories/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("categoryForm") CategoryForm form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, false, null);
            return "ui/categories/form";
        }
        try {
            categoryService.create(form.toCreateRequest());
        } catch (ResourceNotFoundException e) {
            bindingResult.reject("error", e.getMessage());
            populateFormModel(model, false, null);
            return "ui/categories/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Category created.");
        return "redirect:/ui/categories";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("categoryForm")) {
            var category = categoryService.get(id);
            CategoryForm form = new CategoryForm();
            form.setName(category.name());
            form.setDescription(category.description());
            form.setParentCategoryId(category.parentCategoryId());
            form.setDisplayOrder(category.displayOrder());
            form.setActive(category.active());
            if (category.translations() != null) {
                category.translations().forEach((code, translation) -> {
                    form.getNameTranslations().put(code, translation.name());
                    form.getDescriptionTranslations().put(code, translation.description());
                });
            }
            model.addAttribute("categoryForm", form);
        }
        populateFormModel(model, true, id);
        return "ui/categories/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("categoryForm") CategoryForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populateFormModel(model, true, id);
            return "ui/categories/form";
        }
        try {
            categoryService.update(id, form.toUpdateRequest());
        } catch (ResourceNotFoundException e) {
            bindingResult.reject("error", e.getMessage());
            populateFormModel(model, true, id);
            return "ui/categories/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Category updated.");
        return "redirect:/ui/categories";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        categoryService.deactivate(id);
        redirectAttributes.addFlashAttribute("successMessage", "Category deactivated.");
        return "redirect:/ui/categories";
    }

    private void populateFormModel(Model model, boolean isEdit, Long categoryId) {
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("formAction", isEdit ? "/ui/categories/" + categoryId + "/edit" : "/ui/categories/new");
        model.addAttribute("parentCategories", categoryService.list(PageRequest.of(0, 200)).getContent());
        model.addAttribute("nonDefaultSupportedLanguages", nonDefaultSupportedLanguages());
    }

    private List<String> nonDefaultSupportedLanguages() {
        var tenant = tenantService.getCurrent();
        return SupportedLanguage.orderedCodes(Set.copyOf(tenant.supportedLanguageCodes())).stream()
                .filter(code -> !code.equals(tenant.defaultLanguageCode()))
                .toList();
    }
}
