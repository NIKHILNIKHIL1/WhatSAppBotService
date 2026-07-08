package com.bot.whatsappbotservice.whatsapp.twilio;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Receives Twilio's WhatsApp inbound-message webhook (form-urlencoded, not JSON like Meta's). */
@RestController
@RequestMapping("/api/twilio/webhook")
public class TwilioWebhookController {

    private final TwilioWebhookService webhookService;

    public TwilioWebhookController(TwilioWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> receive(
            @RequestParam("From") String from,
            @RequestParam("To") String to,
            @RequestParam(value = "Body", required = false) String body,
            @RequestParam("MessageSid") String messageSid,
            @RequestParam(value = "ProfileName", required = false) String profileName) {
        webhookService.processIncoming(from, to, body, messageSid, profileName);
        return ResponseEntity.ok().build();
    }
}
