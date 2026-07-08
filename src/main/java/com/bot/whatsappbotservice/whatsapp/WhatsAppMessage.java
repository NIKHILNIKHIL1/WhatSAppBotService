package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.common.TenantScopedEntity;
import com.bot.whatsappbotservice.customer.Customer;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "whatsapp_message")
public class WhatsAppMessage extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "wa_message_id", nullable = false, unique = true)
    private String waMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageDirection direction;

    @Column(name = "from_phone_number", nullable = false)
    private String fromPhoneNumber;

    @Column(name = "to_phone_number", nullable = false)
    private String toPhoneNumber;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status = MessageStatus.RECEIVED;
}
