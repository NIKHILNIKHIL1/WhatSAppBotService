package com.bot.whatsappbotservice.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bot.whatsappbotservice.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        auditService = new AuditService(auditLogRepository, new ObjectMapper());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void recordStampsCurrentTenantIdAndSerializesOldAndNewValues() {
        TenantContext.setTenantId(7L);
        when(auditLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        auditService.record("Product", "42", AuditAction.UPDATE, Map.of("price", "10.00"),
                Map.of("price", "12.00"), AuditChannel.API, "alice@example.com");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(7L);
        assertThat(saved.getEntityName()).isEqualTo("Product");
        assertThat(saved.getEntityId()).isEqualTo("42");
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getPerformedBy()).isEqualTo("alice@example.com");
        assertThat(saved.getOldValue()).contains("10.00");
        assertThat(saved.getNewValue()).contains("12.00");
    }

    @Test
    void listAlwaysScopesQueryToCurrentTenantId() {
        TenantContext.setTenantId(9L);
        Page<AuditLog> emptyPage = new PageImpl<>(java.util.List.of());
        when(auditLogRepository.findByTenantId(eq(9L), any())).thenReturn(emptyPage);

        auditService.list(null, PageRequest.of(0, 10));

        verify(auditLogRepository).findByTenantId(eq(9L), any());
        verify(auditLogRepository, never()).findByTenantIdAndEntityName(any(), any(), any());
    }

    @Test
    void listFiltersByEntityNameWhenProvided() {
        TenantContext.setTenantId(9L);
        Page<AuditLog> emptyPage = new PageImpl<>(java.util.List.of());
        when(auditLogRepository.findByTenantIdAndEntityName(eq(9L), eq("Product"), any())).thenReturn(emptyPage);

        auditService.list("Product", PageRequest.of(0, 10));

        verify(auditLogRepository).findByTenantIdAndEntityName(eq(9L), eq("Product"), any());
    }
}
