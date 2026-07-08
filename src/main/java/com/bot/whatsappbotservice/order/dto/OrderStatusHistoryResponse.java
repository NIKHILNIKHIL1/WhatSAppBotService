package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.OrderStatus;
import java.time.Instant;

public record OrderStatusHistoryResponse(
        Long id,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        String notes,
        Instant createdAt
) {
}
