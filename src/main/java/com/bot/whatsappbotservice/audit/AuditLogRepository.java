package com.bot.whatsappbotservice.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByTenantId(Long tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndEntityName(Long tenantId, String entityName, Pageable pageable);
}
