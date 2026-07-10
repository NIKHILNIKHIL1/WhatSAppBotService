package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.DocumentMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ImageMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.InteractiveButtonsMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListSection;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.InteractiveListMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.MediaUploadResponse;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ReplyButton;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.SendMessageResponse;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.TextMessage;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    /** Meta's two-step document flow, step one: upload the raw bytes to get back a media id (no
     * public URL needed, unlike Twilio's document API — see {@code sendDocumentByMediaId}). */
    public String uploadMedia(String phoneNumberId, String accessToken, String filename, String mimeType,
                               byte[] content) {
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");
        body.add("type", mimeType);
        body.add("file", fileResource);

        MediaUploadResponse response = restClient.post()
                .uri("/{phoneNumberId}/media", phoneNumberId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(MediaUploadResponse.class);
        return response != null ? response.id() : null;
    }

    /** Step two of Meta's document flow: send a message referencing the media id from {@code
     * uploadMedia}. */
    public String sendDocumentByMediaId(String phoneNumberId, String accessToken, String to, String mediaId,
                                         String filename, String caption) {
        return send(phoneNumberId, accessToken, DocumentMessage.of(to, mediaId, filename, caption));
    }

    /** Sends an image by media id. Works with an id from {@code uploadMedia} AND with the media id
     * of an image a customer sent inbound — inbound media belongs to the same WABA, so it can be
     * forwarded (e.g. to the vendor's number) without a download/re-upload round trip. */
    public String sendImageByMediaId(String phoneNumberId, String accessToken, String to, String mediaId,
                                      String caption) {
        return send(phoneNumberId, accessToken, ImageMessage.of(to, mediaId, caption));
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
