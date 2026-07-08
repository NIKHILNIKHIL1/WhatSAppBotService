package com.bot.whatsappbotservice.whatsapp;

import java.util.Optional;

/**
 * Persists in-flight WhatsApp conversation state (which step of the order flow a customer is on)
 * keyed by tenant + phone number. {@link RedisWhatsAppSessionStore} is the real implementation;
 * {@link InMemoryWhatsAppSessionStore} backs local dev when {@code app.redis.enabled=false} so the
 * conversation flow can be exercised without a running Redis instance.
 */
public interface WhatsAppSessionStore {

    Optional<WhatsAppSession> get(Long tenantId, String phoneNumber);

    void save(Long tenantId, String phoneNumber, WhatsAppSession session);

    void clear(Long tenantId, String phoneNumber);
}
