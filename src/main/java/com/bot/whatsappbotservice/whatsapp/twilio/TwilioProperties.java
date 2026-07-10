package com.bot.whatsappbotservice.whatsapp.twilio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code webhookUrl} is the exact public URL registered in the Twilio console — the signature is
 * computed over the URL as Twilio sees it, so behind a proxy/tunnel the locally reconstructed URL
 * (http, internal host) would never match. Blank falls back to reconstructing from the request. */
@ConfigurationProperties(prefix = "app.twilio")
public record TwilioProperties(String apiBaseUrl, String webhookUrl) {
}
