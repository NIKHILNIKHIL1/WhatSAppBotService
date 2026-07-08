package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.i18n.Translation;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "categoryId", source = "category.id")
    ProductResponse toResponse(Product product);

    TranslationDto toDto(Translation translation);
}
