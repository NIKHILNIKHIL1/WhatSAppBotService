package com.bot.whatsappbotservice.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productNameSnapshot,
        BigDecimal unitPriceSnapshot,
        BigDecimal quantity,
        BigDecimal lineTotal
) {
}
