package com.bot.whatsappbotservice.catalog.dto;

import com.bot.whatsappbotservice.i18n.TranslationDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        Long parentCategoryId,
        Integer displayOrder,
        Map<String, TranslationDto> translations
) {
}
