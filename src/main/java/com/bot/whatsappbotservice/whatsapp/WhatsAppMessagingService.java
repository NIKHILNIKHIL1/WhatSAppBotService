package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.tenant.MessagingProvider;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListRow;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ListSection;
import com.bot.whatsappbotservice.whatsapp.WhatsAppOutboundMessages.ReplyButton;
import com.bot.whatsappbotservice.whatsapp.twilio.TwilioClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Sits between the conversation flow and the provider-specific transport ({@link WhatsAppClient}
 * for Meta, {@link TwilioClient} for Twilio): sends the message, then always records a
 * {@link WhatsAppMessage} row (SENT or FAILED) so outbound traffic has the same audit trail as
 * inbound. A failed send is logged and swallowed here rather than thrown, so one bad message
 * (e.g. an expired access token) doesn't take down the rest of the conversation turn.
 *
 * <p>Twilio's plain WhatsApp API has no native interactive list/button messages (those require
 * pre-approved Content Templates), so for {@link MessagingProvider#TWILIO} tenants, lists and
 * button prompts are rendered as numbered plain text instead — the customer replies with a
 * number, which {@link WhatsAppConversationService} resolves back to the same option id a Meta
 * button tap would have produced.
 */
@Service
public class WhatsAppMessagingService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppMessagingService.class);

    private final WhatsAppClient whatsAppClient;
    private final TwilioClient twilioClient;
    private final WhatsAppMessageRepository whatsAppMessageRepository;
    private final ObjectMapper objectMapper;
    private final WhatsAppMessages messages;

    public WhatsAppMessagingService(WhatsAppClient whatsAppClient, TwilioClient twilioClient,
                                     WhatsAppMessageRepository whatsAppMessageRepository, ObjectMapper objectMapper,
                                     WhatsAppMessages messages) {
        this.whatsAppClient = whatsAppClient;
        this.twilioClient = twilioClient;
        this.whatsAppMessageRepository = whatsAppMessageRepository;
        this.objectMapper = objectMapper;
        this.messages = messages;
    }

    @Transactional
    public MessageStatus sendText(Tenant tenant, Customer customer, String toPhoneNumber, String body) {
        return sendAndLog(tenant, customer, toPhoneNumber, "text", Map.of("body", body),
                () -> isTwilio(tenant)
                        ? twilioClient.sendText(tenant.getTwilioAccountSid(), tenant.getTwilioAuthToken(),
                                tenant.getTwilioWhatsAppNumber(), toPhoneNumber, body)
                        : whatsAppClient.sendText(tenant.getWhatsappPhoneNumberId(), tenant.getWhatsappAccessToken(),
                                toPhoneNumber, body));
    }

    @Transactional
    public MessageStatus sendInteractiveList(Tenant tenant, Customer customer, String toPhoneNumber, String bodyText,
                                              String buttonLabel, List<ListSection> sections, String languageCode) {
        if (isTwilio(tenant)) {
            String text = renderListAsText(bodyText, sections, languageCode);
            return sendAndLog(tenant, customer, toPhoneNumber, "interactive_list",
                    Map.of("body", bodyText, "sections", sections),
                    () -> twilioClient.sendText(tenant.getTwilioAccountSid(), tenant.getTwilioAuthToken(),
                            tenant.getTwilioWhatsAppNumber(), toPhoneNumber, text));
        }
        return sendAndLog(tenant, customer, toPhoneNumber, "interactive_list",
                Map.of("body", bodyText, "sections", sections),
                () -> whatsAppClient.sendInteractiveList(tenant.getWhatsappPhoneNumberId(),
                        tenant.getWhatsappAccessToken(), toPhoneNumber, bodyText, buttonLabel, sections));
    }

    @Transactional
    public MessageStatus sendInteractiveButtons(Tenant tenant, Customer customer, String toPhoneNumber,
                                                 String bodyText, List<ReplyButton> buttons, String languageCode) {
        if (isTwilio(tenant)) {
            String text = renderButtonsAsText(bodyText, buttons, languageCode);
            return sendAndLog(tenant, customer, toPhoneNumber, "interactive_buttons",
                    Map.of("body", bodyText, "buttons", buttons),
                    () -> twilioClient.sendText(tenant.getTwilioAccountSid(), tenant.getTwilioAuthToken(),
                            tenant.getTwilioWhatsAppNumber(), toPhoneNumber, text));
        }
        return sendAndLog(tenant, customer, toPhoneNumber, "interactive_buttons",
                Map.of("body", bodyText, "buttons", buttons),
                () -> whatsAppClient.sendInteractiveButtons(tenant.getWhatsappPhoneNumberId(),
                        tenant.getWhatsappAccessToken(), toPhoneNumber, bodyText, buttons));
    }

    /** Meta-only — Twilio's WhatsApp API can only send a document by fetching it from a public
     * HTTPS URL, which this app doesn't expose; callers must gate on {@code
     * tenant.getMessagingProvider()} before reaching here (see {@code WhatsAppConversationService}'s
     * order-history flow, the only caller today). */
    @Transactional
    public MessageStatus sendDocument(Tenant tenant, Customer customer, String toPhoneNumber, byte[] documentBytes,
                                       String filename, String caption) {
        return sendAndLog(tenant, customer, toPhoneNumber, "document", Map.of("filename", filename, "caption", caption),
                () -> {
                    String mediaId = whatsAppClient.uploadMedia(tenant.getWhatsappPhoneNumberId(),
                            tenant.getWhatsappAccessToken(), filename, "application/pdf", documentBytes);
                    return whatsAppClient.sendDocumentByMediaId(tenant.getWhatsappPhoneNumberId(),
                            tenant.getWhatsappAccessToken(), toPhoneNumber, mediaId, filename, caption);
                });
    }

    /**
     * Forwards an image. {@code mediaReference} is provider-shaped: for Meta tenants it's a media
     * id (including the id of an inbound customer photo — same WABA, so it forwards directly); for
     * Twilio tenants inbound media arrives as a public URL, which is sent as a link in a text
     * message since plain Twilio has no media-by-id send.
     */
    @Transactional
    public MessageStatus sendImage(Tenant tenant, Customer customer, String toPhoneNumber, String mediaReference,
                                    String caption) {
        if (isTwilio(tenant)) {
            String text = caption + "\n" + mediaReference;
            return sendAndLog(tenant, customer, toPhoneNumber, "image",
                    Map.of("mediaUrl", mediaReference, "caption", caption),
                    () -> twilioClient.sendText(tenant.getTwilioAccountSid(), tenant.getTwilioAuthToken(),
                            tenant.getTwilioWhatsAppNumber(), toPhoneNumber, text));
        }
        return sendAndLog(tenant, customer, toPhoneNumber, "image",
                Map.of("mediaId", mediaReference, "caption", caption),
                () -> whatsAppClient.sendImageByMediaId(tenant.getWhatsappPhoneNumberId(),
                        tenant.getWhatsappAccessToken(), toPhoneNumber, mediaReference, caption));
    }

    private boolean isTwilio(Tenant tenant) {
        return tenant.getMessagingProvider() == MessagingProvider.TWILIO;
    }

    private String renderListAsText(String bodyText, List<ListSection> sections, String languageCode) {
        StringBuilder text = new StringBuilder(bodyText).append('\n');
        int index = 1;
        for (ListSection section : sections) {
            for (ListRow row : section.rows()) {
                text.append(index++).append(". ").append(row.title());
                if (row.description() != null && !row.description().isBlank()) {
                    text.append(" - ").append(row.description());
                }
                text.append('\n');
            }
        }
        text.append(messages.get("bot.list.reply_instruction", languageCode));
        return text.toString();
    }

    private String renderButtonsAsText(String bodyText, List<ReplyButton> buttons, String languageCode) {
        String options = IntStream.range(0, buttons.size())
                .mapToObj(i -> (i + 1) + ". " + buttons.get(i).reply().title())
                .collect(Collectors.joining("\n"));
        return bodyText + "\n" + options + "\n" + messages.get("bot.list.reply_instruction", languageCode);
    }

    private String resolveFromNumber(Tenant tenant) {
        return isTwilio(tenant) ? tenant.getTwilioWhatsAppNumber() : tenant.getWhatsappPhoneNumberId();
    }

    private MessageStatus sendAndLog(Tenant tenant, Customer customer, String toPhoneNumber, String messageType,
                                      Object payloadForLog, MessageSender sender) {
        WhatsAppMessage message = new WhatsAppMessage();
        message.setCustomer(customer);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setFromPhoneNumber(resolveFromNumber(tenant));
        message.setToPhoneNumber(toPhoneNumber);
        message.setMessageType(messageType);
        message.setPayload(toJson(payloadForLog));

        try {
            String waMessageId = sender.send();
            message.setWaMessageId(waMessageId != null ? waMessageId : "local-" + java.util.UUID.randomUUID());
            message.setStatus(MessageStatus.SENT);
        } catch (RestClientException e) {
            log.error("Failed to send WhatsApp {} message to {} for tenant {}", messageType, toPhoneNumber,
                    tenant.getId(), e);
            message.setWaMessageId("failed-" + java.util.UUID.randomUUID());
            message.setStatus(MessageStatus.FAILED);
        }
        whatsAppMessageRepository.save(message);
        return message.getStatus();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    @FunctionalInterface
    private interface MessageSender {
        String send();
    }
}
