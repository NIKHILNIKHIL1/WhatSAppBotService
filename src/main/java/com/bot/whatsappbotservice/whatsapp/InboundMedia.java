package com.bot.whatsappbotservice.whatsapp;

/**
 * Channel-neutral handle to a customer-sent image: {@code reference} is Meta's media id or
 * Twilio's public media URL — {@link WhatsAppMessagingService#sendImage} knows which shape the
 * tenant's provider expects when forwarding it.
 */
public record InboundMedia(String reference, String caption) {
}
