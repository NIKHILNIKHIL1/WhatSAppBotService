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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Entity
@Table(name = "product")
public class Product extends TenantScopedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private String unit;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "INR";

    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Non-default-language translations only — the base {@code name}/{@code description} above
     * already represent the tenant's default language, so it's never duplicated in here. LAZY
     * (default): relies on the caller running inside a transaction (see
     * {@code WhatsAppConversationService.handleMessage}, {@code @Transactional}) since this is
     * loaded well before {@link #getLocalizedName} is typically called.
     */
    @ElementCollection
    @CollectionTable(name = "product_translation", joinColumns = @JoinColumn(name = "product_id"))
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
