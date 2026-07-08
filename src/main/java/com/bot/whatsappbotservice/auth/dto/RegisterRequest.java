package com.bot.whatsappbotservice.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RegisterRequest(
        @NotBlank @Size(max = 255) String tenantName,

        @NotBlank
        @Pattern(regexp = "^[a-z0-9-]{3,100}$", message = "must be lowercase letters, numbers and hyphens only")
        String slug,

        @NotBlank @Size(max = 255) String adminFullName,

        @NotBlank @Email @Size(max = 255) String adminEmail,

        @NotBlank @Size(min = 8, max = 100) String adminPassword,

        String currencyCode,

        String timezone,

        String defaultLanguageCode,

        Set<String> supportedLanguageCodes
) {
}
