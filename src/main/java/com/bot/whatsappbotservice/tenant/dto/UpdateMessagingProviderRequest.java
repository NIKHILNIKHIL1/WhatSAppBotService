package com.bot.whatsappbotservice.tenant.dto;

import com.bot.whatsappbotservice.tenant.MessagingProvider;
import jakarta.validation.constraints.NotNull;

public record UpdateMessagingProviderRequest(
        @NotNull MessagingProvider provider
) {
}
