package com.bot.whatsappbotservice.catalog.dto;

import com.bot.whatsappbotservice.i18n.TranslationDto;
import java.util.Map;

public record CategoryResponse(
        Long id,
        String name,
        String description,
        Long parentCategoryId,
        Integer displayOrder,
        boolean active,
        Map<String, TranslationDto> translations
) {
}
