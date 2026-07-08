package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.dto.CustomerResponse;
import com.bot.whatsappbotservice.ui.form.CustomerForm;
import jakarta.validation.Valid;
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
@RequestMapping("/ui/customers")
public class CustomerUiController {

    private final CustomerService customerService;

    public CustomerUiController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public String list(Model model, Pageable pageable) {
        model.addAttribute("customers", customerService.list(pageable));
        return "ui/customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("customerForm")) {
            model.addAttribute("customerForm", new CustomerForm());
        }
        model.addAttribute("isEdit", false);
        model.addAttribute("formAction", "/ui/customers/new");
        return "ui/customers/form";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("customerForm") CustomerForm form, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            model.addAttribute("formAction", "/ui/customers/new");
            return "ui/customers/form";
        }
        try {
            customerService.create(form.toCreateRequest());
        } catch (DuplicateResourceException e) {
            bindingResult.reject("error", e.getMessage());
            model.addAttribute("isEdit", false);
            model.addAttribute("formAction", "/ui/customers/new");
            return "ui/customers/form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Customer created.");
        return "redirect:/ui/customers";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        if (!model.containsAttribute("customerForm")) {
            CustomerResponse customer = customerService.get(id);
            CustomerForm form = new CustomerForm();
            form.setPhoneNumber(customer.phoneNumber());
            form.setFullName(customer.fullName());
            form.setPreferredLanguageCode(customer.preferredLanguageCode());
            form.setStatus(customer.status());
            model.addAttribute("customerForm", form);
        }
        model.addAttribute("isEdit", true);
        model.addAttribute("formAction", "/ui/customers/" + id + "/edit");
        return "ui/customers/form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("customerForm") CustomerForm form,
                          BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", true);
            model.addAttribute("formAction", "/ui/customers/" + id + "/edit");
            return "ui/customers/form";
        }
        customerService.update(id, form.toUpdateRequest());
        redirectAttributes.addFlashAttribute("successMessage", "Customer updated.");
        return "redirect:/ui/customers";
    }

    @PostMapping("/{id}/block")
    public String block(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        customerService.block(id);
        redirectAttributes.addFlashAttribute("successMessage", "Customer blocked.");
        return "redirect:/ui/customers";
    }
}
