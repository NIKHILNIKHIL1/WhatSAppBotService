package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.tenant.MessagingProvider;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import com.bot.whatsappbotservice.tenant.dto.UpdateMessagingProviderRequest;
import com.bot.whatsappbotservice.ui.form.LanguagesForm;
import com.bot.whatsappbotservice.ui.form.OrderingPolicyForm;
import com.bot.whatsappbotservice.ui.form.TwilioConfigForm;
import com.bot.whatsappbotservice.ui.form.WhatsAppConfigForm;
import jakarta.validation.Valid;
import java.util.LinkedHashSet;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Platform-admin console: tenant (vendor) settings are managed here, per tenant, by the
 * SUPER_ADMIN — the vendor-facing settings page this replaces no longer exists. Same forms and
 * validation as before, but every action names its target tenant explicitly, because the admin
 * has no tenant context of their own.
 */
@Controller
@RequestMapping("/ui/admin/tenants")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminTenantUiController {

    private final TenantService tenantService;

    public AdminTenantUiController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tenants", tenantService.listAll());
        return "ui/admin/tenants";
    }

    @GetMapping("/{id}/settings")
    public String settings(@PathVariable Long id, Model model) {
        TenantProfileResponse tenant = tenantService.getById(id);
        model.addAttribute("tenant", tenant);
        addMissingFormAttributes(model, tenant);
        return "ui/admin/tenant-settings";
    }

    @PostMapping("/{id}/settings/whatsapp")
    public String updateWhatsAppConfig(@PathVariable Long id,
                                        @Valid @ModelAttribute("whatsAppConfigForm") WhatsAppConfigForm form,
                                        BindingResult bindingResult, Model model,
                                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(id, model);
        }
        try {
            tenantService.updateWhatsAppConfig(id, form.toRequest());
        } catch (DuplicateResourceException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "WhatsApp settings updated.");
        return "redirect:/ui/admin/tenants/" + id + "/settings";
    }

    @PostMapping("/{id}/settings/twilio")
    public String updateTwilioConfig(@PathVariable Long id,
                                      @Valid @ModelAttribute("twilioConfigForm") TwilioConfigForm form,
                                      BindingResult bindingResult, Model model,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(id, model);
        }
        try {
            tenantService.updateTwilioConfig(id, form.toRequest());
            tenantService.updateMessagingProvider(id, new UpdateMessagingProviderRequest(
                    form.isActivateTwilio() ? MessagingProvider.TWILIO : MessagingProvider.META));
        } catch (DuplicateResourceException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Twilio settings updated.");
        return "redirect:/ui/admin/tenants/" + id + "/settings";
    }

    @PostMapping("/{id}/settings/languages")
    public String updateLanguages(@PathVariable Long id,
                                   @Valid @ModelAttribute("languagesForm") LanguagesForm form,
                                   BindingResult bindingResult, Model model,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(id, model);
        }
        try {
            tenantService.updateSupportedLanguages(id, form.toRequest());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(id, model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Supported languages updated.");
        return "redirect:/ui/admin/tenants/" + id + "/settings";
    }

    @PostMapping("/{id}/settings/ordering")
    public String updateOrderingPolicy(@PathVariable Long id,
                                        @ModelAttribute("orderingPolicyForm") OrderingPolicyForm form,
                                        RedirectAttributes redirectAttributes) {
        tenantService.updateCustomerRegistrationPolicy(id, form.isRequireCustomerRegistration());
        redirectAttributes.addFlashAttribute("successMessage", "Ordering policy updated.");
        return "redirect:/ui/admin/tenants/" + id + "/settings";
    }

    /** Re-renders the settings page after a validation/business-rule error. All forms are bound
     * on the page, so whichever ones weren't the target of this POST still need attributes. */
    private String rerenderWithError(Long id, Model model) {
        TenantProfileResponse tenant = tenantService.getById(id);
        model.addAttribute("tenant", tenant);
        addMissingFormAttributes(model, tenant);
        return "ui/admin/tenant-settings";
    }

    private void addMissingFormAttributes(Model model, TenantProfileResponse tenant) {
        if (!model.containsAttribute("whatsAppConfigForm")) {
            model.addAttribute("whatsAppConfigForm", new WhatsAppConfigForm());
        }
        if (!model.containsAttribute("twilioConfigForm")) {
            TwilioConfigForm form = new TwilioConfigForm();
            form.setActivateTwilio("TWILIO".equals(tenant.messagingProvider()));
            model.addAttribute("twilioConfigForm", form);
        }
        if (!model.containsAttribute("languagesForm")) {
            LanguagesForm form = new LanguagesForm();
            form.setSupportedLanguageCodes(new LinkedHashSet<>(tenant.supportedLanguageCodes()));
            model.addAttribute("languagesForm", form);
        }
        if (!model.containsAttribute("orderingPolicyForm")) {
            OrderingPolicyForm form = new OrderingPolicyForm();
            form.setRequireCustomerRegistration(tenant.requireCustomerRegistration());
            model.addAttribute("orderingPolicyForm", form);
        }
    }
}
