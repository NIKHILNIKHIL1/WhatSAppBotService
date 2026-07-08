package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.catalog.dto.CreateProductRequest;
import com.bot.whatsappbotservice.catalog.dto.UpdateProductRequest;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ProductForm {

    @NotBlank
    @Size(max = 100)
    private String sku;

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;
    private Long categoryId;

    @NotBlank
    private String unit;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal price;

    private String currencyCode;
    private String imageUrl;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal initialQuantity;

    private boolean active = true;

    /**
     * Two parallel flat maps rather than a nested {@code Map<String,TranslationDto>} — Spring
     * MVC/Thymeleaf binds a plain {@code Map<String,String>} on POST reliably without needing to
     * pre-seed mutable nested objects, which keyed-bean binding would require.
     */
    private Map<String, String> nameTranslations = new LinkedHashMap<>();
    private Map<String, String> descriptionTranslations = new LinkedHashMap<>();

    public CreateProductRequest toCreateRequest() {
        return new CreateProductRequest(sku, name, description, categoryId, unit, price, currencyCode, imageUrl,
                initialQuantity, buildTranslations());
    }

    public UpdateProductRequest toUpdateRequest() {
        return new UpdateProductRequest(name, description, categoryId, unit, price, currencyCode, imageUrl, active,
                buildTranslations());
    }

    private Map<String, TranslationDto> buildTranslations() {
        Map<String, TranslationDto> translations = new LinkedHashMap<>();
        for (String code : nameTranslations.keySet()) {
            String translatedName = nameTranslations.get(code);
            String translatedDescription = descriptionTranslations.get(code);
            if (StringUtils.hasText(translatedName) || StringUtils.hasText(translatedDescription)) {
                translations.put(code, new TranslationDto(translatedName, translatedDescription));
            }
        }
        return translations;
    }
}
