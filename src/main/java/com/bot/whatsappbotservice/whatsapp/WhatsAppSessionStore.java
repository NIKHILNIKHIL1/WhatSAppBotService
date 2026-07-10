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

    /**
     * Claims the right to send an unregistered-contact rejection notice to this number: returns
     * {@code true} exactly once per {@code ttl} window (first caller wins), {@code false} while a
     * previous claim is still live. Keeps a stranger (or bot loop) messaging the number from
     * costing the vendor one paid outbound reply per inbound message.
     */
    boolean tryClaimRejectionNotice(Long tenantId, String phoneNumber, java.time.Duration ttl);
}
