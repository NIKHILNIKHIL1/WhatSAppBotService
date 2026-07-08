package com.bot.whatsappbotservice.whatsapp.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WhatsAppWebhookPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesInboundTextMessage() throws Exception {
        String json = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "WABA_ID",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": { "display_phone_number": "15550001111", "phone_number_id": "PHONE_ID_123" },
                        "contacts": [{ "profile": { "name": "Alice" }, "wa_id": "14155550100" }],
                        "messages": [{
                          "from": "14155550100",
                          "id": "wamid.ABC123",
                          "timestamp": "1720000000",
                          "type": "text",
                          "text": { "body": "hello" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        WhatsAppWebhookPayload payload = objectMapper.readValue(json, WhatsAppWebhookPayload.class);

        WhatsAppWebhookPayload.WebhookValue value = payload.entry().get(0).changes().get(0).value();
        assertThat(value.metadata().phoneNumberId()).isEqualTo("PHONE_ID_123");
        WhatsAppWebhookPayload.InboundMessage message = value.messages().get(0);
        assertThat(message.from()).isEqualTo("14155550100");
        assertThat(message.id()).isEqualTo("wamid.ABC123");
        assertThat(message.textBody()).isEqualTo("hello");
        assertThat(message.replyId()).isNull();
    }

    @Test
    void parsesInteractiveListReply() throws Exception {
        String json = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "WABA_ID",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "metadata": { "phone_number_id": "PHONE_ID_123" },
                        "messages": [{
                          "from": "14155550100",
                          "id": "wamid.LIST1",
                          "type": "interactive",
                          "interactive": {
                            "type": "list_reply",
                            "list_reply": { "id": "cat:5", "title": "Dairy" }
                          }
                        }]
                      }
                    }]
                  }]
                }
                """;

        WhatsAppWebhookPayload payload = objectMapper.readValue(json, WhatsAppWebhookPayload.class);
        WhatsAppWebhookPayload.InboundMessage message =
                payload.entry().get(0).changes().get(0).value().messages().get(0);

        assertThat(message.replyId()).isEqualTo("cat:5");
        assertThat(message.textBody()).isNull();
    }

    @Test
    void parsesInteractiveButtonReply() throws Exception {
        String json = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": { "phone_number_id": "PHONE_ID_123" },
                        "messages": [{
                          "from": "14155550100",
                          "id": "wamid.BTN1",
                          "type": "interactive",
                          "interactive": {
                            "type": "button_reply",
                            "button_reply": { "id": "CONFIRM", "title": "Confirm" }
                          }
                        }]
                      }
                    }]
                  }]
                }
                """;

        WhatsAppWebhookPayload payload = objectMapper.readValue(json, WhatsAppWebhookPayload.class);
        WhatsAppWebhookPayload.InboundMessage message =
                payload.entry().get(0).changes().get(0).value().messages().get(0);

        assertThat(message.replyId()).isEqualTo("CONFIRM");
    }

    @Test
    void statusOnlyPayloadHasNoMessages() throws Exception {
        String json = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": { "phone_number_id": "PHONE_ID_123" },
                        "statuses": [{ "id": "wamid.OUT1", "status": "delivered", "recipient_id": "14155550100" }]
                      }
                    }]
                  }]
                }
                """;

        WhatsAppWebhookPayload payload = objectMapper.readValue(json, WhatsAppWebhookPayload.class);
        WhatsAppWebhookPayload.WebhookValue value = payload.entry().get(0).changes().get(0).value();

        assertThat(value.messages()).isNull();
        assertThat(value.statuses().get(0).status()).isEqualTo("delivered");
    }
}
