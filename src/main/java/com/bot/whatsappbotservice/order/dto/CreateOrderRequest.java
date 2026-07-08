package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.OrderChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long customerId,
        OrderChannel channel,
        @NotEmpty @Valid List<OrderItemRequest> items,
        String notes,
        String idempotencyKey
) {
}
