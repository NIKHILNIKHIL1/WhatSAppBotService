package com.bot.whatsappbotservice.customer.dto;

import com.bot.whatsappbotservice.customer.CustomerStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerRequest(
        String fullName,
        String preferredLanguageCode,
        @NotNull CustomerStatus status
) {
}
