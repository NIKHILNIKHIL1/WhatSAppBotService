package com.bot.whatsappbotservice.ui.form;

import com.bot.whatsappbotservice.catalog.dto.CreateCategoryRequest;
import com.bot.whatsappbotservice.catalog.dto.UpdateCategoryRequest;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class CategoryForm {

    @NotBlank
    @Size(max = 255)
    private String name;

    private String description;
    private Long parentCategoryId;
    private Integer displayOrder;
    private boolean active = true;

    /**
     * Two parallel flat maps rather than a nested {@code Map<String,TranslationDto>} — Spring
     * MVC/Thymeleaf binds a plain {@code Map<String,String>} on POST reliably without needing to
     * pre-seed mutable nested objects, which keyed-bean binding would require.
     */
    private Map<String, String> nameTranslations = new LinkedHashMap<>();
    private Map<String, String> descriptionTranslations = new LinkedHashMap<>();

    public CreateCategoryRequest toCreateRequest() {
        return new CreateCategoryRequest(name, description, parentCategoryId, displayOrder, buildTranslations());
    }

    public UpdateCategoryRequest toUpdateRequest() {
        return new UpdateCategoryRequest(name, description, parentCategoryId, displayOrder, active,
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
