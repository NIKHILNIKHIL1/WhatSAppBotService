package com.bot.whatsappbotservice.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Spring's autoconfigured {@link MessageSource} (backed by
 * {@code classpath:i18n/messages*.properties}, see {@code spring.messages.basename}) so callers
 * resolve bot copy by a raw language code string instead of juggling {@link Locale} directly.
 */
@Component
public class WhatsAppMessages {

    private final MessageSource messageSource;

    public WhatsAppMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * {@code args} is passed as {@code null} (not an empty array) when the caller supplies none —
     * Spring only runs a message through {@link java.text.MessageFormat} when {@code args != null},
     * and {@code MessageFormat} treats a bare {@code '} as a quote delimiter. Plain strings (the
     * majority of keys) should never have to worry about escaping apostrophes; only genuinely
     * parameterized keys ({@code {0}}, {@code {1}}, ...) need {@code ''} for a literal apostrophe.
     */
    public String get(String key, String languageCode, Object... args) {
        Locale locale = Locale.forLanguageTag(SupportedLanguage.isSupported(languageCode) ? languageCode : "en");
        return messageSource.getMessage(key, args.length == 0 ? null : args, locale);
    }

    /**
     * The very first message a customer sees, before we know their language — so unlike every
     * other lookup this can't be resolved in a single locale. Built from each supported
     * language's own bundle: an English intro/instruction (a reasonable universal default for a
     * "pick your language" prompt) plus every supported language's self-name, numbered in
     * {@link SupportedLanguage}'s fixed order.
     */
    /**
     * Resolves {@code key + "_named"} with the customer's name as argument {@code {0}} (the plain
     * key's arguments shift up by one) when a name is known, else the plain key unchanged — one
     * convention for every personalized bot message, so "Thank you, Priya!" never degrades to
     * "Thank you, null!".
     */
    public String getPersonalized(String key, String languageCode, String customerName, Object... args) {
        if (customerName == null || customerName.isBlank()) {
            return get(key, languageCode, args);
        }
        Object[] shifted = new Object[args.length + 1];
        shifted[0] = customerName.strip();
        System.arraycopy(args, 0, shifted, 1, args.length);
        return get(key + "_named", languageCode, shifted);
    }

    public String languagePrompt(Set<String> supportedLanguageCodes) {
        return languagePrompt(null, supportedLanguageCodes);
    }

    /** Greets the customer by name ("Welcome, Priya!") when one is known — with registration
     * mandatory that's the normal case; a blank name falls back to the impersonal greeting. */
    public String languagePrompt(String customerName, Set<String> supportedLanguageCodes) {
        List<String> ordered = SupportedLanguage.orderedCodes(supportedLanguageCodes);
        String intro = customerName != null && !customerName.isBlank()
                ? get("language.prompt.intro_named", "en", customerName.strip())
                : get("language.prompt.intro", "en");
        StringBuilder sb = new StringBuilder(intro);
        for (int i = 0; i < ordered.size(); i++) {
            sb.append('\n').append(i + 1).append(". ").append(get("language.self-name", ordered.get(i)));
        }
        sb.append("\n\n").append(get("language.prompt.instruction", "en"));
        return sb.toString();
    }
}
