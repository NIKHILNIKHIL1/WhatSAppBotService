package com.bot.whatsappbotservice.inventory.event;

/**
 * Published when a stock adjustment drops quantity-on-hand from above the reorder level to at or
 * below it (a crossing — not on every sale while already low, so the vendor gets exactly one alert
 * per dip; restocking above the level re-arms it). Minimal payload by design, mirroring
 * {@code order.event.OrderStatusChangedEvent}: the AFTER_COMMIT listener re-reads current state,
 * so a stale snapshot can never be sent.
 */
public record LowStockEvent(Long tenantId, Long productId) {
}
