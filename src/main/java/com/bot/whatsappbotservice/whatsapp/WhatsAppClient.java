package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.InteractiveButtonsMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListSection;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.InteractiveListMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ReplyButton;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.SendMessageResponse;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.TextMessage;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Thin wrapper around the Meta WhatsApp Cloud API "send message" endpoint. Each tenant sends
 * from its own WhatsApp Business phone number using its own access token. */
@Component
public class WhatsAppClient {

    private final RestClient restClient;

    public WhatsAppClient(RestClient.Builder restClientBuilder, WhatsAppProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.graphApiBaseUrl()).build();
    }

    public String sendText(String phoneNumberId, String accessToken, String to, String body) {
        return send(phoneNumberId, accessToken, TextMessage.of(to, body));
    }

    public String sendInteractiveList(String phoneNumberId, String accessToken, String to, String bodyText,
                                       String buttonLabel, List<ListSection> sections) {
        return send(phoneNumberId, accessToken, InteractiveListMessage.of(to, bodyText, buttonLabel, sections));
    }

    public String sendInteractiveButtons(String phoneNumberId, String accessToken, String to, String bodyText,
                                          List<ReplyButton> buttons) {
        return send(phoneNumberId, accessToken, InteractiveButtonsMessage.of(to, bodyText, buttons));
    }

    private String send(String phoneNumberId, String accessToken, Object requestBody) {
        SendMessageResponse response = restClient.post()
                .uri("/{phoneNumberId}/messages", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(SendMessageResponse.class);
        return response != null && !response.messages().isEmpty() ? response.messages().get(0).id() : null;
    }
}
