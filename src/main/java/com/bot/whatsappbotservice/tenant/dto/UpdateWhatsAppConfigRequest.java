package com.bot.whatsappbotservice.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateWhatsAppConfigRequest(
        @NotBlank String whatsappPhoneNumberId,
        String whatsappBusinessAccountId,
        @NotBlank String whatsappAccessToken,
        String vendorNotificationPhoneNumber
) {
}
