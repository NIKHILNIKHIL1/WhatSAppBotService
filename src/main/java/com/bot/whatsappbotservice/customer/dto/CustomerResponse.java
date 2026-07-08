package com.bot.whatsappbotservice.customer.dto;

import com.bot.whatsappbotservice.customer.CustomerStatus;

public record CustomerResponse(
        Long id,
        String phoneNumber,
        String fullName,
        String preferredLanguageCode,
        CustomerStatus status
) {
}
