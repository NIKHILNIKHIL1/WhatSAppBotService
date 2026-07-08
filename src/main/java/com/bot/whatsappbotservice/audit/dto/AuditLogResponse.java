package com.bot.whatsappbotservice.audit.dto;

import com.bot.whatsappbotservice.audit.AuditAction;
import com.bot.whatsappbotservice.audit.AuditChannel;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        String entityName,
        String entityId,
        AuditAction action,
        JsonNode oldValue,
        JsonNode newValue,
        String performedBy,
        String ipAddress,
        AuditChannel channel,
        Instant createdAt
) {
}
