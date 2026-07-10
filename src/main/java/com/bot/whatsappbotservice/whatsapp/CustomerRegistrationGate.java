package com.bot.whatsappbotservice.whatsapp;

import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.customer.CustomerService;
import com.bot.whatsappbotservice.customer.CustomerStatus;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.tenant.Tenant;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Decides, per inbound WhatsApp message, whether the sender is allowed to transact with this
 * tenant. Shared by the Meta and Twilio webhook services so both channels enforce identical rules:
 *
 * <ul>
 *   <li>BLOCKED customers are always refused, even when the tenant allows open ordering.</li>
 *   <li>With {@code requireCustomerRegistration} (the default), unknown numbers are refused; the
 *       vendor must register the customer (name + WhatsApp number) first.</li>
 *   <li>Without it, first contact auto-registers, preserving the original open behavior.</li>
 * </ul>
 *
 * <p>Refused senders get one polite notice in the tenant's default language (their preferred
 * language is unknown — they were never registered), throttled to one per number per
 * {@link #REJECTION_NOTICE_COOLDOWN} so inbound spam can't generate unbounded paid outbound
 * replies. After the notice, silence.
 */
@Component
public class CustomerRegistrationGate {

    private static final Logger log = LoggerFactory.getLogger(CustomerRegistrationGate.class);
    private static final Duration REJECTION_NOTICE_COOLDOWN = Duration.ofHours(24);

    private final CustomerService customerService;
    private final WhatsAppSessionStore sessionStore;
    private final WhatsAppMessagingService messagingService;
    private final WhatsAppMessages messages;

    public CustomerRegistrationGate(CustomerService customerService, WhatsAppSessionStore sessionStore,
                                     WhatsAppMessagingService messagingService, WhatsAppMessages messages) {
        this.customerService = customerService;
        this.sessionStore = sessionStore;
        this.messagingService = messagingService;
        this.messages = messages;
    }

    /**
     * @param phoneNumber the sender's number, already normalized to {@code +E.164}
     * @return the customer allowed to transact, or empty when the message must not be processed
     *         (a throttled rejection notice has then already been handled here)
     */
    public Optional<Customer> resolveTransactingCustomer(Tenant tenant, String phoneNumber, String profileName) {
        if (phoneNumber == null) {
            return Optional.empty();
        }
        Optional<Customer> existing = customerService.findRegisteredByPhoneNumber(phoneNumber, profileName);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == CustomerStatus.BLOCKED) {
                log.info("Blocked customer {} messaged tenant {}; refusing", existing.get().getId(), tenant.getId());
                sendRejectionNoticeThrottled(tenant, phoneNumber);
                return Optional.empty();
            }
            return existing;
        }
        if (!tenant.isRequireCustomerRegistration()) {
            return Optional.of(customerService.findOrCreateByPhoneNumber(phoneNumber, null, profileName));
        }
        log.info("Unregistered number {} messaged tenant {} (registration required); refusing",
                phoneNumber, tenant.getId());
        sendRejectionNoticeThrottled(tenant, phoneNumber);
        return Optional.empty();
    }

    private void sendRejectionNoticeThrottled(Tenant tenant, String phoneNumber) {
        if (!sessionStore.tryClaimRejectionNotice(tenant.getId(), phoneNumber, REJECTION_NOTICE_COOLDOWN)) {
            return;
        }
        String lang = tenant.getDefaultLanguageCode();
        String vendorPhone = tenant.getVendorNotificationPhoneNumber();
        String contactLine = (vendorPhone != null && !vendorPhone.isBlank())
                ? messages.get("bot.help.contact_line", lang, vendorPhone)
                : messages.get("bot.help.no_contact_line", lang);
        messagingService.sendText(tenant, null, phoneNumber,
                messages.get("bot.customer.not_registered", lang, contactLine));
    }
}
