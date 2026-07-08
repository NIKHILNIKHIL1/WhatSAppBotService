package com.bot.whatsappbotservice.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp.meta")
public record WhatsAppProperties(String graphApiBaseUrl, String verifyToken) {
}
