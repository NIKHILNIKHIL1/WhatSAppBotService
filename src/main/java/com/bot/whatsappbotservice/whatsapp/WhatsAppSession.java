package com.bot.whatsappbotservice.whatsapp;

import java.util.ArrayList;
import java.util.List;

public record WhatsAppSession(
        ConversationStep step,
        String languageCode,
        Long categoryId,
        Long selectedProductId,
        List<CartLine> cart,
        List<String> lastOptionIds
) {
    public static WhatsAppSession initial() {
        return new WhatsAppSession(ConversationStep.LANGUAGE_SELECTION, null, null, null, new ArrayList<>(),
                List.of());
    }

    public WhatsAppSession withStep(ConversationStep newStep) {
        return new WhatsAppSession(newStep, languageCode, categoryId, selectedProductId, cart, lastOptionIds);
    }

    public WhatsAppSession withLanguage(String newLanguageCode) {
        return new WhatsAppSession(step, newLanguageCode, categoryId, selectedProductId, cart, lastOptionIds);
    }

    public WhatsAppSession withCategory(Long newCategoryId) {
        return new WhatsAppSession(step, languageCode, newCategoryId, selectedProductId, cart, lastOptionIds);
    }

    public WhatsAppSession withSelectedProduct(Long productId) {
        return new WhatsAppSession(step, languageCode, categoryId, productId, cart, lastOptionIds);
    }

    public WhatsAppSession withCartLineAdded(CartLine line) {
        List<CartLine> updated = new ArrayList<>(cart);
        updated.add(line);
        return new WhatsAppSession(step, languageCode, categoryId, null, updated, lastOptionIds);
    }

    public WhatsAppSession withEmptyCart() {
        return new WhatsAppSession(step, languageCode, null, null, new ArrayList<>(), lastOptionIds);
    }

    public WhatsAppSession withLastOptionIds(List<String> newLastOptionIds) {
        return new WhatsAppSession(step, languageCode, categoryId, selectedProductId, cart, newLastOptionIds);
    }
}
