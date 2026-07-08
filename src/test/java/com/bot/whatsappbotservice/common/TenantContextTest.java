package com.bot.whatsappbotservice.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void settingTenantIdMirrorsItIntoMdc() {
        TenantContext.setTenantId(42L);

        assertThat(TenantContext.getTenantId()).isEqualTo(42L);
        assertThat(MDC.get("tenantId")).isEqualTo("42");
    }

    @Test
    void settingNullTenantIdRemovesMdcEntry() {
        TenantContext.setTenantId(42L);
        TenantContext.setTenantId(null);

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void clearRemovesBothThreadLocalAndMdc() {
        TenantContext.setTenantId(7L);

        TenantContext.clear();

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }
}
