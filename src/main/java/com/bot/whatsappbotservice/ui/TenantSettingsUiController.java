package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.common.exception.DuplicateResourceException;
import com.bot.whatsappbotservice.tenant.MessagingProvider;
import com.bot.whatsappbotservice.tenant.TenantService;
import com.bot.whatsappbotservice.tenant.dto.TenantProfileResponse;
import com.bot.whatsappbotservice.tenant.dto.UpdateMessagingProviderRequest;
import com.bot.whatsappbotservice.ui.form.LanguagesForm;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ui/settings")
@PreAuthorize("hasRole('VENDOR_ADMIN')")
public class TenantSettingsUiController {

    private final TenantService tenantService;

    public TenantSettingsUiController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    public String view(Model model) {
        TenantProfileResponse tenant = tenantService.getCurrent();
        model.addAttribute("tenant", tenant);
        addMissingFormAttributes(model, tenant);
        return "ui/settings";
    }

    @PostMapping("/whatsapp")
    public String updateWhatsAppConfig(@Valid @ModelAttribute("whatsAppConfigForm") WhatsAppConfigForm form,
                                        BindingResult bindingResult, Model model,
                                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(model);
        }
        try {
            tenantService.updateWhatsAppConfig(form.toRequest());
        } catch (DuplicateResourceException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "WhatsApp settings updated.");
        return "redirect:/ui/settings";
    }

    @PostMapping("/twilio")
    public String updateTwilioConfig(@Valid @ModelAttribute("twilioConfigForm") TwilioConfigForm form,
                                      BindingResult bindingResult, Model model,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(model);
        }
        try {
            tenantService.updateTwilioConfig(form.toRequest());
            tenantService.updateMessagingProvider(new UpdateMessagingProviderRequest(
                    form.isActivateTwilio() ? MessagingProvider.TWILIO : MessagingProvider.META));
        } catch (DuplicateResourceException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Twilio settings updated.");
        return "redirect:/ui/settings";
    }

    @PostMapping("/languages")
    public String updateLanguages(@Valid @ModelAttribute("languagesForm") LanguagesForm form,
                                   BindingResult bindingResult, Model model,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return rerenderWithError(model);
        }
        try {
            tenantService.updateSupportedLanguages(form.toRequest());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            return rerenderWithError(model);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Supported languages updated.");
        return "redirect:/ui/settings";
    }

    /** Re-renders ui/settings after a validation/business-rule error. All forms are bound in the
     * page, so whichever ones weren't the target of this POST still need an attribute to bind to. */
    private String rerenderWithError(Model model) {
        TenantProfileResponse tenant = tenantService.getCurrent();
        model.addAttribute("tenant", tenant);
        addMissingFormAttributes(model, tenant);
        return "ui/settings";
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
    }
}
