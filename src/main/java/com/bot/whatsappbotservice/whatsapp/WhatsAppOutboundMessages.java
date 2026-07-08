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
