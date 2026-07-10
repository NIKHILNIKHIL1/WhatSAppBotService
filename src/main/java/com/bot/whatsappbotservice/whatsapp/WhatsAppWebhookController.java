package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WhatsAppWebhookService webhookService;
    private final MetaSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppWebhookService webhookService,
                                      MetaSignatureVerifier signatureVerifier, ObjectMapper objectMapper) {
        this.properties = properties;
        this.webhookService = webhookService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
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

    // Binds the raw bytes (not the payload record) because the X-Hub-Signature-256 HMAC is
    // computed over the body exactly as Meta sent it; deserialization happens only after the
    // signature proves the request actually came from Meta.
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!signatureVerifier.verify(rawBody, signature)) {
            log.warn("Rejected WhatsApp webhook POST with missing or invalid X-Hub-Signature-256");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WhatsAppWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawBody, WhatsAppWebhookPayload.class);
        } catch (IOException e) {
            log.warn("Rejected WhatsApp webhook POST with unparseable body: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        webhookService.processIncoming(payload);
        return ResponseEntity.ok().build();
    }
}
