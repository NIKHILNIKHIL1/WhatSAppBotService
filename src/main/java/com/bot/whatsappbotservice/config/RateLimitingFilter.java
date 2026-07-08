package com.bot.whatsappbotservice.config;

import com.bot.whatsappbotservice.common.HttpRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Simple fixed-window request counter (INCR + EXPIRE-on-first-hit) backed by Redis, applied only
 * to the two publicly reachable, unauthenticated endpoint groups: login/registration (brute-force
 * / signup-spam protection) and the WhatsApp webhook (protection against direct flooding of a
 * public URL). Everything else is already behind JWT auth, which is a much stronger gate.
 *
 * <p>If Redis itself is unreachable, requests are allowed through rather than rejected — rate
 * limiting is defense in depth, not core functionality, and a Redis blip must not become a full
 * outage of the auth or webhook endpoints.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitingFilter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!properties.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        RateLimitProperties.Window window;
        String bucket;
        if (path.startsWith("/api/auth/")) {
            window = properties.auth();
            bucket = "auth";
        } else if (path.startsWith("/api/whatsapp/webhook") || path.startsWith("/api/twilio/webhook")) {
            window = properties.webhook();
            bucket = "webhook";
        } else {
            filterChain.doFilter(request, response);
            return;
        }

        String key = "ratelimit:" + bucket + ":" + HttpRequestUtils.resolveClientIp(request);
        long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(window.windowSeconds()));
            }
        } catch (Exception e) {
            log.warn("Rate limiter could not reach Redis; failing open for {}", path, e);
            filterChain.doFilter(request, response);
            return;
        }

        if (count > window.requestsPerWindow()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"success\":false,\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\","
                            + "\"message\":\"Too many requests, please try again later\"}}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
