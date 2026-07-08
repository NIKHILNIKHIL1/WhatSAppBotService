package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppWebhookService webhookService) {
        this.properties = properties;
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(mode) && verifyToken != null && properties.verifyToken() != null
                && properties.verifyToken().equals(verifyToken)) {
            return ResponseEntity.ok(challenge);
        }
        log.warn("Rejected WhatsApp webhook verification attempt (mode={})", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping
    public ResponseEntity<Void> receive(@RequestBody WhatsAppWebhookPayload payload) {
        webhookService.processIncoming(payload);
        return ResponseEntity.ok().build();
    }
}
