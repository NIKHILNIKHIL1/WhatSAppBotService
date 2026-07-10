package com.bot.whatsappbotservice.order;

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

/** A customer-raised concern (photo of a damaged/wrong delivery sent over WhatsApp). {@code order}
 * is nullable — the photo arrived but the customer had no recent order to pin it to. */
@Getter
@Setter
@Entity
@Table(name = "order_concern")
public class OrderConcern extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private OrderHeader order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "media_reference", length = 500)
    private String mediaReference;

    @Column(columnDefinition = "text")
    private String caption;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConcernStatus status = ConcernStatus.OPEN;
}
