package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status,
        String notes
) {
}
