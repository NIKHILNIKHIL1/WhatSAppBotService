package com.bot.whatsappbotservice.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

/**
 * Base class for every tenant-owned entity. {@code @TenantId} makes Hibernate append
 * {@code tenant_id = :currentTenant} to every query for subclasses and stamp it on insert,
 * using whatever {@link org.hibernate.context.spi.CurrentTenantIdentifierResolver} is registered
 * (see {@code config.TenantIdentifierResolver}) — so tenant isolation can't be bypassed by a
 * repository method that forgets to filter explicitly.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantScopedEntity extends BaseEntity {

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;
}
