package com.bot.whatsappbotservice.order;

/**
 * Unlike {@link OrderStatus}, payment status is never set directly by a caller — it is derived
 * from the running {@code amountPaid} vs {@code totalAmount} inside
 * {@code OrderService.recordPayment} (and {@code refundPayment} for {@link #REFUNDED}), so no
 * transition map is needed here: the arithmetic is the state machine.
 */
public enum PaymentStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    REFUNDED
}
