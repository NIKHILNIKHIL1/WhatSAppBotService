package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        Long customerId,
        String customerName,
        String customerPhoneNumber,
        OrderStatus status,
        OrderChannel channel,
        String currencyCode,
        BigDecimal subtotalAmount,
        BigDecimal totalAmount,
        String notes,
        List<OrderItemResponse> items,
        Instant createdAt
) {
}
