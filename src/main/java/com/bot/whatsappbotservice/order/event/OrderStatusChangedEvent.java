package com.bot.whatsappbotservice.order.event;

import com.bot.whatsappbotservice.order.OrderStatus;

public record OrderStatusChangedEvent(Long tenantId, Long orderId, OrderStatus fromStatus, OrderStatus toStatus) {
}
