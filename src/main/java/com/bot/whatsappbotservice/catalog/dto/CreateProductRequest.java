package com.bot.whatsappbotservice.catalog.dto;

import com.bot.whatsappbotservice.i18n.TranslationDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Map;

public record CreateProductRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 255) String name,
        String description,
        Long categoryId,
        @NotBlank String unit,
        @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal price,
        String currencyCode,
        String imageUrl,
        @DecimalMin(value = "0", inclusive = true) BigDecimal initialQuantity,
        Map<String, TranslationDto> translations
) {
}
