package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.tenant.Tenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Every storefront page needs the tenant slug (to build nav links) and store name in its model —
 * centralized here so each controller method in this package doesn't have to repeat it.
 */
@ControllerAdvice(basePackageClasses = StorefrontAuthController.class)
public class StorefrontModelAdvice {

    @ModelAttribute("slug")
    public String slug(@PathVariable(required = false) String slug) {
        return slug;
    }

    @ModelAttribute("storeName")
    public String storeName(HttpServletRequest request) {
        Object tenant = request.getAttribute(StorefrontTenantContextFilter.RESOLVED_TENANT_ATTRIBUTE);
        return tenant instanceof Tenant t ? t.getName() : null;
    }
}
