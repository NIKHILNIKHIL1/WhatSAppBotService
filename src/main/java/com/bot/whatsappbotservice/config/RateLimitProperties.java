package com.bot.whatsappbotservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(boolean enabled, Window auth, Window webhook, Window otpRequest) {

    public record Window(int requestsPerWindow, int windowSeconds) {
    }
}
