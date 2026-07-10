package com.bot.whatsappbotservice.whatsapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisWhatsAppSessionStore implements WhatsAppSessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisWhatsAppSessionStore.class);
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisWhatsAppSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<WhatsAppSession> get(Long tenantId, String phoneNumber) {
        String json = redisTemplate.opsForValue().get(key(tenantId, phoneNumber));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, WhatsAppSession.class));
        } catch (JsonProcessingException e) {
            log.warn("Discarding corrupted WhatsApp session for tenant {} phone {}", tenantId, phoneNumber, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(Long tenantId, String phoneNumber, WhatsAppSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key(tenantId, phoneNumber), json, SESSION_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize WhatsApp session", e);
        }
    }

    @Override
    public void clear(Long tenantId, String phoneNumber) {
        redisTemplate.delete(key(tenantId, phoneNumber));
    }

    @Override
    public boolean tryClaimRejectionNotice(Long tenantId, String phoneNumber, Duration ttl) {
        // SET NX EX — atomic claim; the key simply expiring re-opens the window.
        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent("wa:rejection-notice:" + tenantId + ":" + phoneNumber, "1", ttl);
        return Boolean.TRUE.equals(claimed);
    }

    private String key(Long tenantId, String phoneNumber) {
        return "wa:session:" + tenantId + ":" + phoneNumber;
    }
}
