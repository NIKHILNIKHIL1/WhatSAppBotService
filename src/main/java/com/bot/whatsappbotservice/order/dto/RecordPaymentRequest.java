package com.bot.whatsappbotservice.order.dto;

import com.bot.whatsappbotservice.order.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RecordPaymentRequest(
        @NotNull PaymentMethod method,
        @NotNull @Positive BigDecimal amount,
        @Size(max = 150) String reference
) {
}
