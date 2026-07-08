package com.bot.whatsappbotservice.tenant;

import com.bot.whatsappbotservice.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Deliberately NOT tenant-scoped ({@code @TenantId}): login happens by email before any tenant
 * is known, and a SUPER_ADMIN has no tenant at all. Tenant isolation for this table is enforced
 * by application logic (email is globally unique) rather than the Hibernate tenant filter.
 */
@Getter
@Setter
@Entity
@Table(name = "tenant_user")
public class TenantUser extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
}
