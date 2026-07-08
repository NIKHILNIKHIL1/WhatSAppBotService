package com.bot.whatsappbotservice.whatsapp;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Process-local stand-in for {@link RedisWhatsAppSessionStore}, active when
 * {@code app.redis.enabled=false}. State is lost on restart and isn't shared across instances —
 * fine for single-node local dev, not a substitute for Redis in docker/prod.
 */
@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "false")
public class InMemoryWhatsAppSessionStore implements WhatsAppSessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryWhatsAppSessionStore.class);
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private record Entry(WhatsAppSession session, Instant expiresAt) {
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public InMemoryWhatsAppSessionStore() {
        log.warn("app.redis.enabled=false — using in-memory WhatsApp session store (dev/testing only, "
                + "state is lost on restart and not shared across instances)");
    }

    @Override
    public Optional<WhatsAppSession> get(Long tenantId, String phoneNumber) {
        Entry entry = sessions.get(key(tenantId, phoneNumber));
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            sessions.remove(key(tenantId, phoneNumber));
            return Optional.empty();
        }
        return Optional.of(entry.session());
    }

    @Override
    public void save(Long tenantId, String phoneNumber, WhatsAppSession session) {
        sessions.put(key(tenantId, phoneNumber), new Entry(session, Instant.now().plus(SESSION_TTL)));
    }

    @Override
    public void clear(Long tenantId, String phoneNumber) {
        sessions.remove(key(tenantId, phoneNumber));
    }

    private String key(Long tenantId, String phoneNumber) {
        return tenantId + ":" + phoneNumber;
    }
}
