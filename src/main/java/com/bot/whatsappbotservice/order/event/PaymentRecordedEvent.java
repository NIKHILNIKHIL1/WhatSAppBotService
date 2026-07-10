package com.bot.whatsappbotservice.order.event;

import com.bot.whatsappbotservice.order.PaymentStatus;

/** Published after a payment is recorded against an order (same after-commit listener pattern as
 * {@link OrderStatusChangedEvent}); {@code newStatus} lets listeners react only to full payment. */
public record PaymentRecordedEvent(Long tenantId, Long orderId, PaymentStatus newStatus) {
}
