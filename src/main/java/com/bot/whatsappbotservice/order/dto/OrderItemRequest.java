package com.bot.whatsappbotservice.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OrderItemRequest(
        @NotNull Long productId,
        @NotNull @DecimalMin(value = "0.001") BigDecimal quantity
) {
}
