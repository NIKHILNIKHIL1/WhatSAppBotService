package com.bot.whatsappbotservice.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTwilioConfigRequest(
        @NotBlank String twilioAccountSid,
        @NotBlank String twilioAuthToken,
        @NotBlank String twilioWhatsAppNumber
) {
}
