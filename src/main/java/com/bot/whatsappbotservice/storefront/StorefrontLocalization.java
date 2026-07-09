package com.bot.whatsappbotservice.storefront;

import com.bot.whatsappbotservice.catalog.dto.CategoryResponse;
import com.bot.whatsappbotservice.catalog.dto.ProductResponse;
import com.bot.whatsappbotservice.i18n.TranslationDto;
import java.util.Map;
import java.util.function.Function;
import org.springframework.util.StringUtils;

/**
 * Picks a localized product/category name or description for the storefront, operating only on
 * already-materialized DTOs (never the LAZY entity-level {@code translations} map) — see
 * {@code Product.translations}/{@code Category.translations}: with {@code spring.jpa.open-in-view}
 * disabled, the Hibernate session is long closed by the time a view renders, so touching the lazy
 * map here would throw {@code LazyInitializationException}. {@code ProductService.list()}/
 * {@code CategoryService.list()} already materialize {@code translations()} into these DTOs while
 * still inside their own {@code @Transactional} method, which is what makes this safe.
 */
final class StorefrontLocalization {

    private StorefrontLocalization() {
    }

    static String productName(ProductResponse product, String languageCode) {
        return pick(product.name(), product.translations(), languageCode, TranslationDto::name);
    }

    static String productDescription(ProductResponse product, String languageCode) {
        return pick(product.description(), product.translations(), languageCode, TranslationDto::description);
    }

    static String categoryName(CategoryResponse category, String languageCode) {
        return pick(category.name(), category.translations(), languageCode, TranslationDto::name);
    }

    static String categoryDescription(CategoryResponse category, String languageCode) {
        return pick(category.description(), category.translations(), languageCode, TranslationDto::description);
    }

    private static String pick(String base, Map<String, TranslationDto> translations, String languageCode,
                                Function<TranslationDto, String> field) {
        if (translations == null || languageCode == null) {
            return base;
        }
        TranslationDto translation = translations.get(languageCode);
        if (translation == null) {
            return base;
        }
        String value = field.apply(translation);
        return StringUtils.hasText(value) ? value : base;
    }
}
