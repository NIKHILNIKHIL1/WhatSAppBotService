package com.bot.whatsappbotservice.catalog;

import com.bot.whatsappbotservice.common.TenantScopedEntity;
import com.bot.whatsappbotservice.i18n.Translation;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Entity
@Table(name = "category")
public class Category extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id")
    private Category parentCategory;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    /** See {@code Product.translations} for the fallback/fetch-type rationale — identical here. */
    @ElementCollection
    @CollectionTable(name = "category_translation", joinColumns = @JoinColumn(name = "category_id"))
    @MapKeyColumn(name = "language_code")
    private Map<String, Translation> translations = new HashMap<>();

    public String getLocalizedName(String languageCode) {
        Translation translation = translations.get(languageCode);
        return (translation != null && StringUtils.hasText(translation.getName())) ? translation.getName() : name;
    }

    public String getLocalizedDescription(String languageCode) {
        Translation translation = translations.get(languageCode);
        return (translation != null && StringUtils.hasText(translation.getDescription()))
                ? translation.getDescription() : description;
    }
}
