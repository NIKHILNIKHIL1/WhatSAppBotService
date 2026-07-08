package com.bot.whatsappbotservice.tenant.dto;

import java.util.List;

public record TenantProfileResponse(
        Long id,
        String name,
        String slug,
        String whatsappPhoneNumberId,
        String whatsappBusinessAccountId,
        boolean whatsappAccessTokenConfigured,
        String vendorNotificationPhoneNumber,
        String defaultLanguageCode,
        String currencyCode,
        String timezone,
        String status,
        String messagingProvider,
        String twilioWhatsAppNumber,
        boolean twilioConfigured,
        List<String> supportedLanguageCodes
) {
}
