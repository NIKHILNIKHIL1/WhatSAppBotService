package com.bot.whatsappbotservice.whatsapp.twilio;

import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Twilio's WhatsApp inbound-message webhook (form-urlencoded, not JSON like Meta's).
 *
 * <p>Every request is authenticated against {@code X-Twilio-Signature} before processing. The
 * signature is keyed with the account auth token, which lives on the {@link Tenant} — so the
 * tenant is resolved here (by the "To" number) just to fetch that token; the async
 * {@link TwilioWebhookService} still does its own resolution when it processes the message.
 * Binding a {@code MultiValueMap} rather than individual params is deliberate: the HMAC covers
 * every posted parameter, including ones this app doesn't otherwise care about.
 */
@RestController
@RequestMapping("/api/twilio/webhook")
public class TwilioWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TwilioWebhookController.class);
    private static final String WHATSAPP_PREFIX = "whatsapp:";

    private final TwilioWebhookService webhookService;
    private final TwilioSignatureVerifier signatureVerifier;
    private final TenantRepository tenantRepository;
    private final TwilioProperties properties;

    public TwilioWebhookController(TwilioWebhookService webhookService, TwilioSignatureVerifier signatureVerifier,
                                    TenantRepository tenantRepository, TwilioProperties properties) {
        this.webhookService = webhookService;
        this.signatureVerifier = signatureVerifier;
        this.tenantRepository = tenantRepository;
        this.properties = properties;
    }

    @PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> receive(
            @RequestParam MultiValueMap<String, String> params,
            @RequestHeader(value = "X-Twilio-Signature", required = false) String signature,
            HttpServletRequest request) {
        String from = params.getFirst("From");
        String to = params.getFirst("To");
        String messageSid = params.getFirst("MessageSid");
        if (from == null || to == null || messageSid == null) {
            return ResponseEntity.badRequest().build();
        }

        Tenant tenant = tenantRepository.findByTwilioWhatsAppNumber(stripPrefix(to)).orElse(null);
        if (tenant == null) {
            // Same outcome as before signature validation existed: log and drop with a 2xx so
            // Twilio doesn't retry a message no tenant will ever handle.
            log.warn("No tenant configured for Twilio WhatsApp number {}; dropping message", stripPrefix(to));
            return ResponseEntity.ok().build();
        }
        if (tenant.getTwilioAuthToken() == null || tenant.getTwilioAuthToken().isBlank()) {
            log.error("Tenant {} has no Twilio auth token configured; cannot validate X-Twilio-Signature — "
                    + "rejecting webhook", tenant.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!signatureVerifier.verify(requestUrl(request), params.toSingleValueMap(), signature,
                tenant.getTwilioAuthToken())) {
            log.warn("Rejected Twilio webhook POST with missing or invalid X-Twilio-Signature (tenant {})",
                    tenant.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        webhookService.processIncoming(from, to, params.getFirst("Body"), messageSid,
                params.getFirst("ProfileName"));
        return ResponseEntity.ok().build();
    }

    private String requestUrl(HttpServletRequest request) {
        if (properties.webhookUrl() != null && !properties.webhookUrl().isBlank()) {
            return properties.webhookUrl();
        }
        String query = request.getQueryString();
        return query == null ? request.getRequestURL().toString() : request.getRequestURL() + "?" + query;
    }

    private String stripPrefix(String value) {
        return value.startsWith(WHATSAPP_PREFIX) ? value.substring(WHATSAPP_PREFIX.length()) : value;
    }
}
