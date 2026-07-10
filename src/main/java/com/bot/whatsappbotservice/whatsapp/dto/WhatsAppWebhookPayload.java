package com.bot.whatsappbotservice.whatsapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Mirrors the (much larger) Meta WhatsApp Cloud API webhook payload — only the fields this app
 * actually reads are modeled; everything else is ignored rather than failing deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WhatsAppWebhookPayload(String object, List<WebhookEntry> entry) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookEntry(String id, List<WebhookChange> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookChange(WebhookValue value, String field) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookValue(
            WebhookMetadata metadata,
            List<WebhookContact> contacts,
            List<InboundMessage> messages,
            List<StatusUpdate> statuses) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookMetadata(
            @JsonProperty("display_phone_number") String displayPhoneNumber,
            @JsonProperty("phone_number_id") String phoneNumberId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookContact(WebhookProfile profile, @JsonProperty("wa_id") String waId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookProfile(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InboundMessage(
            String from,
            String id,
            String timestamp,
            String type,
            TextBody text,
            InteractiveReply interactive,
            ButtonReply button,
            ImageContent image) {

        /** The selection id from whichever reply shape is present, or null for free text. */
        public String replyId() {
            if (interactive != null && interactive.listReply() != null) {
                return interactive.listReply().id();
            }
            if (interactive != null && interactive.buttonReply() != null) {
                return interactive.buttonReply().id();
            }
            return null;
        }

        public String textBody() {
            return text != null ? text.body() : null;
        }

        public String imageMediaId() {
            return image != null ? image.id() : null;
        }

        public String imageCaption() {
            return image != null ? image.caption() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextBody(String body) {
    }

    /** A photo the customer sent: {@code id} is Meta's media id (the bytes live on Meta's CDN and
     * can be re-sent by id within the same WABA), {@code caption} the text under it, if any. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageContent(String id, @JsonProperty("mime_type") String mimeType, String caption) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InteractiveReply(
            String type,
            @JsonProperty("list_reply") ReplySelection listReply,
            @JsonProperty("button_reply") ReplySelection buttonReply) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReplySelection(String id, String title) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ButtonReply(String text, String payload) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusUpdate(
            String id,
            String status,
            String timestamp,
            @JsonProperty("recipient_id") String recipientId) {
    }
}
