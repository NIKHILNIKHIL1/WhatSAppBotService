package com.bot.whatsappbotservice.config;

import com.bot.whatsappbotservice.common.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

/**
 * Tells Hibernate which tenant the current thread belongs to, backing every
 * {@code @TenantId}-annotated entity's automatic row filtering. Falls back to a sentinel
 * ("0") for the small number of operations that run before a tenant is resolved
 * (e.g. authentication itself, or the WhatsApp webhook before it looks up the tenant).
 */
@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<Object> {

    private static final Long NO_TENANT = 0L;

    @Override
    public Object resolveCurrentTenantIdentifier() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId != null ? tenantId : NO_TENANT;
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
