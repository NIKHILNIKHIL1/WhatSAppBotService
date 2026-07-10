package com.bot.whatsappbotservice.whatsapp.twilio;

import com.bot.whatsappbotservice.common.PhoneNumbers;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.CustomerRegistrationGate;
import com.bot.whatsappbotservice.whatsapp.MessageDirection;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import com.bot.whatsappbotservice.whatsapp.WhatsAppConversationService;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessage;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Twilio counterpart to {@code WhatsAppWebhookService}: resolves which tenant an inbound Twilio
 * message belongs to (by the tenant's Twilio WhatsApp number), then hands off to the same
 * {@link WhatsAppConversationService} used by the Meta channel, so both providers drive one
 * conversation engine. Twilio has no native reply-id concept (no button/list taps), so this
 * always passes a {@code null} replyId — {@link WhatsAppConversationService} falls back to
 * resolving a bare numeric reply against the last list of options it sent.
 */
@Service
public class TwilioWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioWebhookService.class);
    private static final String WHATSAPP_PREFIX = "whatsapp:";

    private final TenantRepository tenantRepository;
    private final CustomerRegistrationGate registrationGate;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final WhatsAppConversationService conversationService;
    private final ObjectMapper objectMapper;

    public TwilioWebhookService(TenantRepository tenantRepository, CustomerRegistrationGate registrationGate,
                                 WhatsAppMessageRepository whatsAppMessageRepository,
                                 WhatsAppConversationService conversationService, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.registrationGate = registrationGate;
        this.whatsAppMessageRepository = whatsAppMessageRepository;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    @Async("whatsappTaskExecutor")
    public void processIncoming(String from, String to, String body, String messageSid, String profileName) {
        processIncoming(from, to, body, messageSid, profileName, null);
    }

    @Async("whatsappTaskExecutor")
    public void processIncoming(String from, String to, String body, String messageSid, String profileName,
                                 String mediaUrl) {
        try {
            String fromNumber = stripPrefix(from);
            String toNumber = stripPrefix(to);

            Tenant tenant = tenantRepository.findByTwilioWhatsAppNumber(toNumber).orElse(null);
            if (tenant == null) {
                logger.warn("No tenant configured for Twilio WhatsApp number {}; dropping message", toNumber);
                return;
            }

            TenantContext.setTenantId(tenant.getId());
            try {
                processMessage(tenant, fromNumber, toNumber, body, messageSid, profileName, mediaUrl);
            } finally {
                TenantContext.clear();
            }
        } catch (Exception e) {
            logger.error("Unhandled error processing Twilio webhook payload", e);
        }
    }

    private void processMessage(Tenant tenant, String fromNumber, String toNumber, String body, String messageSid,
                                 String profileName, String mediaUrl) {
        if (whatsAppMessageRepository.existsByWaMessageId(messageSid)) {
            logger.debug("Duplicate Twilio message {} for tenant {}; already processed, skipping",
                    messageSid, tenant.getId());
            return;
        }

        String phoneNumber = PhoneNumbers.toE164(fromNumber);
        Customer customer = registrationGate.resolveTransactingCustomer(tenant, phoneNumber, profileName)
                .orElse(null);

        // Record the message as seen BEFORE running the conversation flow, mirroring the Meta
        // webhook: a Twilio retry must not replay this message and risk a duplicate order.
        // Refused contacts are logged too (with no customer) — audit trail + notice-retry guard.
        WhatsAppMessage inboundLog = new WhatsAppMessage();
        inboundLog.setCustomer(customer);
        inboundLog.setWaMessageId(messageSid);
        inboundLog.setDirection(MessageDirection.INBOUND);
        inboundLog.setFromPhoneNumber(phoneNumber != null ? phoneNumber : fromNumber);
        inboundLog.setToPhoneNumber(toNumber);
        inboundLog.setMessageType("text");
        inboundLog.setPayload(toJson(Map.of("body", body != null ? body : "", "profileName",
                profileName != null ? profileName : "")));
        inboundLog.setStatus(MessageStatus.RECEIVED);
        whatsAppMessageRepository.save(inboundLog);

        if (customer == null) {
            return;
        }
        // Twilio delivers media as a public URL; the message Body doubles as the photo caption.
        com.bot.whatsappbotservice.whatsapp.InboundMedia media = mediaUrl != null && !mediaUrl.isBlank()
                ? new com.bot.whatsappbotservice.whatsapp.InboundMedia(mediaUrl, body)
                : null;
        conversationService.handleMessage(tenant, customer, messageSid, body, null, media);
    }

    private String stripPrefix(String value) {
        if (value == null) {
            return null;
        }
        return value.startsWith(WHATSAPP_PREFIX) ? value.substring(WHATSAPP_PREFIX.length()) : value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
