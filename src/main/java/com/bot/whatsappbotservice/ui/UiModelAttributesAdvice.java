package com.bot.whatsappbotservice.ui;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Adds nav/identity attributes to every UI page's model so individual controllers and templates
 * don't each need to know how to read them off the security principal. Deliberately plain Java
 * role checks against {@code Authentication#getAuthorities()} rather than the
 * {@code sec:authorize} Thymeleaf dialect — see the plan notes on skipping
 * {@code thymeleaf-extras-springsecurity} to avoid an unverified Spring Security 7 compatibility
 * gamble.
 */
@ControllerAdvice(basePackages = "com.bot.whatsappbotservice.ui")
public class UiModelAttributesAdvice {

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    @ModelAttribute("isVendorAdmin")
    public boolean isVendorAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_VENDOR_ADMIN".equals(authority.getAuthority()));
    }
}
