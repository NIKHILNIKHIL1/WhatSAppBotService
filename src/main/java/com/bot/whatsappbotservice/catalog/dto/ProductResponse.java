package com.bot.whatsappbotservice.catalog.dto;

import com.bot.whatsappbotservice.i18n.TranslationDto;
import java.math.BigDecimal;
import java.util.Map;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        Long categoryId,
        String unit,
        BigDecimal price,
        String currencyCode,
        String imageUrl,
        boolean active,
        Map<String, TranslationDto> translations
) {
}
