package com.bot.whatsappbotservice.order.dto;

import java.math.BigDecimal;

/** Dashboard tile: orders still owing money (UNPAID / PARTIALLY_PAID, excluding cancelled). */
public record OutstandingPaymentsSummary(long orderCount, BigDecimal amountOutstanding) {
}
