package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.OrderChannel;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.order.PaymentMethod;
import com.bot.whatsappbotservice.order.PaymentStatus;
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
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        BigDecimal amountPaid,
        Instant paidAt,
        String paymentReference,
        String notes,
        List<OrderItemResponse> items,
        Instant createdAt
) {

    /** Outstanding balance; what the UI shows next to a PARTIALLY_PAID badge. */
    public BigDecimal amountDue() {
        if (totalAmount == null || amountPaid == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.subtract(amountPaid);
    }
}
