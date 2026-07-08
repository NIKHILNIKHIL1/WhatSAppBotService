package com.bot.whatsappbotservice.customer;

import com.bot.whatsappbotservice.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "customer")
public class Customer extends TenantScopedEntity {

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "preferred_language_code", nullable = false)
    private String preferredLanguageCode = "en";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CustomerStatus status = CustomerStatus.ACTIVE;
}
