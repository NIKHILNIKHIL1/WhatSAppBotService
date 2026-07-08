package com.bot.whatsappbotservice.whatsapp.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(String apiBaseUrl) {
}
