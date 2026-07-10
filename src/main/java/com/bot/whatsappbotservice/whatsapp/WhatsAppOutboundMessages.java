package com.bot.whatsappbotservice.whatsapp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request/response shapes for the Meta WhatsApp Cloud API "send message" endpoint. */
final class WhatsAppOutboundMessages {

    private WhatsAppOutboundMessages() {
    }

    record TextMessage(
            @JsonProperty("messaging_product") String messagingProduct,
            String to,
            String type,
            TextBody text) {
        static TextMessage of(String to, String body) {
            return new TextMessage("whatsapp", to, "text", new TextBody(body));
        }
    }

    record TextBody(String body) {
    }

    record InteractiveListMessage(
            @JsonProperty("messaging_product") String messagingProduct,
            String to,
            String type,
            Interactive interactive) {
        static InteractiveListMessage of(String to, String bodyText, String buttonLabel, List<ListSection> sections) {
            return new InteractiveListMessage("whatsapp", to, "interactive",
                    new Interactive("list", new InteractiveBody(bodyText),
                            new InteractiveAction(buttonLabel, sections, null)));
        }
    }

    record InteractiveButtonsMessage(
            @JsonProperty("messaging_product") String messagingProduct,
            String to,
            String type,
            Interactive interactive) {
        static InteractiveButtonsMessage of(String to, String bodyText, List<ReplyButton> buttons) {
            return new InteractiveButtonsMessage("whatsapp", to, "interactive",
                    new Interactive("button", new InteractiveBody(bodyText),
                            new InteractiveAction(null, null, buttons)));
        }
    }

    record DocumentMessage(
            @JsonProperty("messaging_product") String messagingProduct,
            String to,
            String type,
            DocumentPayload document) {
        static DocumentMessage of(String to, String mediaId, String filename, String caption) {
            return new DocumentMessage("whatsapp", to, "document",
                    new DocumentPayload(mediaId, filename, caption));
        }
    }

    record DocumentPayload(String id, String filename, String caption) {
    }

    record ImageMessage(
            @JsonProperty("messaging_product") String messagingProduct,
            String to,
            String type,
            ImagePayload image) {
        static ImageMessage of(String to, String mediaId, String caption) {
            return new ImageMessage("whatsapp", to, "image", new ImagePayload(mediaId, caption));
        }
    }

    record ImagePayload(String id, String caption) {
    }

    /** Response shape of Meta's {@code POST /{phoneNumberId}/media} upload endpoint. */
    record MediaUploadResponse(String id) {
    }

    record Interactive(String type, InteractiveBody body, InteractiveAction action) {
    }

    record InteractiveBody(String text) {
    }

    record InteractiveAction(String button, List<ListSection> sections, List<ReplyButton> buttons) {
    }

    record ListSection(String title, List<ListRow> rows) {
    }

    record ListRow(String id, String title, String description) {
    }

    record ReplyButton(String type, ReplyButtonInner reply) {
        static ReplyButton of(String id, String title) {
            return new ReplyButton("reply", new ReplyButtonInner(id, title));
        }
    }

    record ReplyButtonInner(String id, String title) {
    }

    record SendMessageResponse(List<MessageId> messages) {
    }

    record MessageId(String id) {
    }
}
