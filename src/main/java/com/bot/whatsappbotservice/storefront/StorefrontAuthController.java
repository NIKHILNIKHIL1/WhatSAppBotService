package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.common.exception.BusinessRuleViolationException;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.storefront.form.LoginForm;
import com.bot.whatsappbotservice.storefront.form.VerifyForm;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
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

/**
 * Phone+OTP login for the customer storefront (no password). The app's {@code GlobalExceptionHandler}
 * (used elsewhere) doesn't apply here — it's REST-only — so domain exceptions from
 * {@link CustomerOtpService} are caught explicitly and re-rendered, same as {@code OrderUiController}.
 */
@Controller
@RequestMapping("/store/{slug}")
public class StorefrontAuthController {

    private final CustomerOtpService otpService;
    private final SecurityContextRepository securityContextRepository;

    public StorefrontAuthController(CustomerOtpService otpService,
                                     SecurityContextRepository storefrontSecurityContextRepository) {
        this.otpService = otpService;
        this.securityContextRepository = storefrontSecurityContextRepository;
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        if (!model.containsAttribute("loginForm")) {
            model.addAttribute("loginForm", new LoginForm());
        }
        return "store/login";
    }

    @PostMapping("/login")
    public String requestOtp(@PathVariable String slug, @Valid @ModelAttribute("loginForm") LoginForm form,
                              BindingResult bindingResult, HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "store/login";
        }
        Tenant tenant = resolvedTenant(request);
        MessageStatus status;
        try {
            status = otpService.requestOtp(tenant, form.getPhoneNumber());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            return "store/login";
        }
        if (status == MessageStatus.FAILED) {
            bindingResult.reject("error", "We couldn't send a code to that number. Please check it and try again.");
            return "store/login";
        }
        redirectAttributes.addFlashAttribute("successMessage", "A code has been sent to your WhatsApp.");
        redirectAttributes.addAttribute("phone", form.getPhoneNumber());
        return "redirect:/store/" + slug + "/verify";
    }

    @GetMapping("/verify")
    public String verifyForm(@RequestParam(required = false) String phone, Model model) {
        if (!model.containsAttribute("verifyForm")) {
            VerifyForm form = new VerifyForm();
            form.setPhoneNumber(phone);
            model.addAttribute("verifyForm", form);
        }
        return "store/verify";
    }

    @PostMapping("/verify")
    public String verify(@PathVariable String slug, @Valid @ModelAttribute("verifyForm") VerifyForm form,
                          BindingResult bindingResult, HttpServletRequest request, HttpServletResponse response,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "store/verify";
        }
        Customer customer;
        try {
            customer = otpService.verify(form.getPhoneNumber(), form.getCode());
        } catch (BusinessRuleViolationException e) {
            bindingResult.reject("error", e.getMessage());
            return "store/verify";
        }
        establishSession(customer, request, response);
        redirectAttributes.addFlashAttribute("successMessage", "Welcome!");
        return "redirect:/store/" + slug;
    }

    /**
     * Since Spring Security 5.7, {@code SecurityContextHolderFilter} only *loads* the context at
     * the start of the chain — it does not save it back. Setting {@code SecurityContextHolder}'s
     * context alone would silently not survive to the next request; the explicit
     * {@code saveContext} call below is what form-login does automatically and what this OTP flow
     * must replicate by hand.
     */
    private void establishSession(Customer customer, HttpServletRequest request, HttpServletResponse response) {
        CustomerPrincipal principal = new CustomerPrincipal(
                customer.getId(), customer.getTenantId(), customer.getPhoneNumber(),
                customer.getPreferredLanguageCode());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private Tenant resolvedTenant(HttpServletRequest request) {
        return (Tenant) request.getAttribute(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE);
    }
}
