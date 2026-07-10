package com.bot.whatsappbotservice.order.event;

/** Published when a vendor marks a customer concern resolved — after-commit listeners turn it
 * into a WhatsApp update to the customer, same pattern as {@link OrderStatusChangedEvent}. */
public record ConcernResolvedEvent(Long tenantId, Long concernId) {
}
