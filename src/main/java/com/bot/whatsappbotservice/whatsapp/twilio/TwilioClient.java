package com.bot.whatsappbotservice.whatsapp.twilio;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Thin wrapper around the Twilio Messages REST API for sending WhatsApp messages. Twilio
 * requires the {@code whatsapp:} scheme prefix on phone numbers; every other component in this
 * package works with plain E.164 numbers, so the prefix is added/stripped only here. */
@Component
public class TwilioClient {

    private final RestClient restClient;

    public TwilioClient(RestClient.Builder restClientBuilder, TwilioProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.apiBaseUrl()).build();
    }

    public String sendText(String accountSid, String authToken, String fromWhatsAppNumber, String to, String body) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", "whatsapp:" + fromWhatsAppNumber);
        form.add("To", "whatsapp:" + to);
        form.add("Body", body);

        TwilioMessageResponse response = restClient.post()
                .uri("/Accounts/{accountSid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TwilioMessageResponse.class);
        return response != null ? response.sid() : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TwilioMessageResponse(String sid) {
    }
}
