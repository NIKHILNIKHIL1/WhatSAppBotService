package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.common.PhoneNumbers;
import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload.InboundMessage;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload.WebhookChange;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload.WebhookEntry;
import com.bot.whatsappbotservice.whatsapp.dto.WhatsAppWebhookPayload.WebhookValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Resolves which tenant an inbound webhook message belongs to (by WABA phone_number_id), sets
 * {@link TenantContext} for the duration of processing, and hands off to
 * {@link WhatsAppConversationService}. Runs off the request thread via {@code @Async} so the
 * controller can ack Meta immediately; {@link TenantContext} is cleared in a {@code finally} block
 * because this method runs on a pooled thread that will be reused for unrelated tenants later.
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    private final TenantRepository tenantRepository;
    private final CustomerRegistrationGate registrationGate;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final WhatsAppConversationService conversationService;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookService(TenantRepository tenantRepository, CustomerRegistrationGate registrationGate,
                                   WhatsAppMessageRepository whatsAppMessageRepository,
                                   WhatsAppConversationService conversationService, ObjectMapper objectMapper) {
        this.tenantRepository = tenantRepository;
        this.registrationGate = registrationGate;
        this.whatsAppMessageRepository = whatsAppMessageRepository;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
    }

    @Async("whatsappTaskExecutor")
    public void processIncoming(WhatsAppWebhookPayload payload) {
        try {
            for (WebhookEntry entry : nullSafe(payload.entry())) {
                for (WebhookChange change : nullSafe(entry.changes())) {
                    processChange(change.value());
                }
            }
        } catch (Exception e) {
            logger.error("Unhandled error processing WhatsApp webhook payload", e);
        }
    }

    private void processChange(WebhookValue value) {
        if (value == null || value.messages() == null || value.messages().isEmpty()) {
            return; // delivery/read status callbacks or an empty change — nothing for us to do
        }
        String phoneNumberId = value.metadata() != null ? value.metadata().phoneNumberId() : null;
        if (phoneNumberId == null) {
            logger.warn("WhatsApp webhook message missing metadata.phone_number_id; dropping");
            return;
        }

        Tenant tenant = tenantRepository.findByWhatsappPhoneNumberId(phoneNumberId).orElse(null);
        if (tenant == null) {
            logger.warn("No tenant configured for WhatsApp phone_number_id {}; dropping message", phoneNumberId);
            return;
        }

        TenantContext.setTenantId(tenant.getId());
        try {
            for (InboundMessage message : value.messages()) {
                processMessage(tenant, message, resolveContactName(value, message.from()));
            }
        } finally {
            TenantContext.clear();
        }
    }

    /** Meta sends the sender's WhatsApp profile name alongside the message, keyed by wa_id. */
    private String resolveContactName(WebhookValue value, String from) {
        if (value.contacts() == null) {
            return null;
        }
        return value.contacts().stream()
                .filter(contact -> from.equals(contact.waId()) && contact.profile() != null)
                .map(contact -> contact.profile().name())
                .findFirst()
                .orElse(null);
    }

    private void processMessage(Tenant tenant, InboundMessage message, String contactName) {
        if (whatsAppMessageRepository.existsByWaMessageId(message.id())) {
            logger.debug("Duplicate WhatsApp message {} for tenant {}; already processed, skipping",
                    message.id(), tenant.getId());
            return;
        }

        // Meta sends the wa_id without a '+'; everything downstream (customer identity, session
        // keys, outbound sends) works on the canonical +E.164 form.
        String phoneNumber = PhoneNumbers.toE164(message.from());
        Customer customer = registrationGate.resolveTransactingCustomer(tenant, phoneNumber, contactName)
                .orElse(null);

        // Record the message as seen BEFORE running the conversation flow: if flow processing
        // throws partway through, a Meta webhook retry must not replay this message and risk a
        // duplicate order — better to drop one conversational turn than double-charge a customer.
        // Refused contacts are logged too (with no customer), so the vendor has an audit trail of
        // who tried to order and a retry can never re-send the rejection notice.
        WhatsAppMessage inboundLog = new WhatsAppMessage();
        inboundLog.setCustomer(customer);
        inboundLog.setWaMessageId(message.id());
        inboundLog.setDirection(MessageDirection.INBOUND);
        inboundLog.setFromPhoneNumber(phoneNumber != null ? phoneNumber : message.from());
        inboundLog.setToPhoneNumber(tenant.getWhatsappPhoneNumberId());
        inboundLog.setMessageType(message.type() != null ? message.type() : "unknown");
        inboundLog.setPayload(toJson(message));
        inboundLog.setStatus(MessageStatus.RECEIVED);
        whatsAppMessageRepository.save(inboundLog);

        if (customer == null) {
            return;
        }
        InboundMedia media = message.imageMediaId() != null
                ? new InboundMedia(message.imageMediaId(), message.imageCaption())
                : null;
        conversationService.handleMessage(tenant, customer, message.id(), message.textBody(), message.replyId(),
                media);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
