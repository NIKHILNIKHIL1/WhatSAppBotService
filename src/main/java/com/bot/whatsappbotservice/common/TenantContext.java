package com.bot.whatsappbotservice.common;

import org.slf4j.MDC;

/**
 * Holds the resolved tenant id for the current request thread. Populated by the JWT auth filter
 * (web/API callers) or the WhatsApp webhook handler (resolved from the WABA phone number id)
 * before any service/repository code runs, and must be cleared at the end of the request.
 *
 * <p>Also mirrors the value into SLF4J's MDC under {@code tenantId} so every log line emitted
 * while a tenant is scoped carries it automatically — kept here rather than duplicated at each
 * call site so the ThreadLocal and the MDC entry can never drift out of sync.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final String MDC_KEY = "tenantId";

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
        if (tenantId != null) {
            MDC.put(MDC_KEY, String.valueOf(tenantId));
        } else {
            MDC.remove(MDC_KEY);
        }
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        MDC.remove(MDC_KEY);
    }
}
