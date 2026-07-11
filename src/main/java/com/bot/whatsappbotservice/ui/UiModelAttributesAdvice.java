package com.bot.whatsappbotservice.ui;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
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

    private final TenantRepository tenantRepository;

    public UiModelAttributesAdvice(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /** The vendor's business name for the header brand; null for the super admin (no tenant) and
     * unauthenticated pages, where the layout falls back to the product name. */
    @ModelAttribute("tenantName")
    public String tenantName() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findById(tenantId).map(Tenant::getName).orElse(null);
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getName();
    }

    @ModelAttribute("isVendorAdmin")
    public boolean isVendorAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_VENDOR_ADMIN");
    }

    @ModelAttribute("isSuperAdmin")
    public boolean isSuperAdmin(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_SUPER_ADMIN");
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }
}
