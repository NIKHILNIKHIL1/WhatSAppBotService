package com.bot.whatsappbotservice.tenant.dto;

import java.util.Set;

public record UpdateSupportedLanguagesRequest(Set<String> supportedLanguageCodes) {
}
