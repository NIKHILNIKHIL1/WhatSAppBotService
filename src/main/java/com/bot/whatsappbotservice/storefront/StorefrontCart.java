package com.bot.whatsappbotservice.storefront;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A plain {@code HttpSession} attribute — no DB table. Deliberately doesn't snapshot product
 * name/price (unlike the WhatsApp bot's own {@code whatsapp.CartLine}, which snapshots a
 * session-language-localized name for a single conversation turn): the storefront re-resolves
 * product data live on every cart render, so price changes and {@code ?lang=} language switches
 * are always reflected, and a customer switching devices mid-session just starts a fresh cart —
 * acceptable for v1.
 */
public class StorefrontCart implements Serializable {

    private final Map<Long, BigDecimal> quantitiesByProductId = new LinkedHashMap<>();

    public void setQuantity(Long productId, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            quantitiesByProductId.remove(productId);
        } else {
            quantitiesByProductId.put(productId, quantity);
        }
    }

    public void remove(Long productId) {
        quantitiesByProductId.remove(productId);
    }

    public void clear() {
        quantitiesByProductId.clear();
    }

    public Map<Long, BigDecimal> lines() {
        return quantitiesByProductId;
    }

    public boolean isEmpty() {
        return quantitiesByProductId.isEmpty();
    }
}
