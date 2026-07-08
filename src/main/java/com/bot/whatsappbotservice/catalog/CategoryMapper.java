package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.i18n.Translation;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(target = "parentCategoryId", source = "parentCategory.id")
    CategoryResponse toResponse(Category category);

    TranslationDto toDto(Translation translation);
}
