package com.bot.whatsappbotservice.audit;

import com.bot.whatsappbotservice.audit.dto.AuditLogResponse;
import com.bot.whatsappbotservice.common.HttpRequestUtils;
import com.bot.whatsappbotservice.common.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String entityName, String entityId, AuditAction action, Object oldValue, Object newValue,
                        AuditChannel channel) {
        record(entityName, entityId, action, oldValue, newValue, channel, resolvePerformedBy());
    }

    @Transactional
    public void record(String entityName, String entityId, AuditAction action, Object oldValue, Object newValue,
                        AuditChannel channel, String performedBy) {
        AuditLog entry = new AuditLog();
        entry.setTenantId(TenantContext.getTenantId());
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setOldValue(toJson(oldValue));
        entry.setNewValue(toJson(newValue));
        entry.setPerformedBy(performedBy);
        entry.setIpAddress(resolveIpAddress());
        entry.setChannel(channel);
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(String entityName, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        Page<AuditLog> page = StringUtils.hasText(entityName)
                ? auditLogRepository.findByTenantIdAndEntityName(tenantId, entityName, pageable)
                : auditLogRepository.findByTenantId(tenantId, pageable);
        return page.map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog entry) {
        return new AuditLogResponse(
                entry.getId(), entry.getEntityName(), entry.getEntityId(), entry.getAction(),
                parseJson(entry.getOldValue()), parseJson(entry.getNewValue()), entry.getPerformedBy(),
                entry.getIpAddress(), entry.getChannel(), entry.getCreatedAt());
    }

    private JsonNode parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize audit value", e);
            return null;
        }
    }

    private String resolvePerformedBy() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "system";
    }

    private String resolveIpAddress() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return HttpRequestUtils.resolveClientIp(attrs.getRequest());
    }
}
