package com.bot.whatsappbotservice.i18n;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The fixed set of languages this platform supports, end to end (tenant onboarding, catalog
 * translations, bot copy). Adding a fifth language means adding an enum constant here plus a
 * {@code messages_xx.properties} bundle — nothing else in the codebase enumerates languages
 * independently, so this is the single source of truth for both the allowed codes and their
 * display order.
 */
public enum SupportedLanguage {
    EN("en", "English", "English"),
    FR("fr", "French", "Français"),
    WO("wo", "Wolof", "Wolof"),
    HI("hi", "Hindi", "हिन्दी");

    private final String code;
    private final String englishName;
    private final String nativeName;

    SupportedLanguage(String code, String englishName, String nativeName) {
        this.code = code;
        this.englishName = englishName;
        this.nativeName = nativeName;
    }

    public String code() {
        return code;
    }

    public String englishName() {
        return englishName;
    }

    public String nativeName() {
        return nativeName;
    }

    /** Used to resolve a customer's free-text reply during language selection. */
    public boolean matches(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String trimmed = text.trim();
        return code.equalsIgnoreCase(trimmed) || englishName.equalsIgnoreCase(trimmed)
                || nativeName.equalsIgnoreCase(trimmed);
    }

    public static boolean isSupported(String code) {
        return fromCode(code).isPresent();
    }

    public static Optional<SupportedLanguage> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(l -> l.code.equalsIgnoreCase(code)).findFirst();
    }

    /**
     * Orders the given codes by this enum's declaration order. {@code Set<String>} iteration
     * order is not guaranteed once round-tripped through Hibernate, so anything user-facing
     * (numbered prompts, checkbox lists) must go through this rather than iterating a raw set.
     */
    public static List<String> orderedCodes(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(values())
                .map(SupportedLanguage::code)
                .filter(codes::contains)
                .toList();
    }
}
