package com.bot.whatsappbotservice.tenant;

import com.bot.whatsappbotservice.common.BaseEntity;
import com.bot.whatsappbotservice.common.crypto.EncryptedStringConverter;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "whatsapp_phone_number_id", unique = true)
    private String whatsappPhoneNumberId;

    @Column(name = "whatsapp_business_account_id")
    private String whatsappBusinessAccountId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "whatsapp_access_token")
    private String whatsappAccessToken;

    @Column(name = "vendor_notification_phone_number")
    private String vendorNotificationPhoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "messaging_provider", nullable = false)
    private MessagingProvider messagingProvider = MessagingProvider.META;

    @Column(name = "twilio_account_sid")
    private String twilioAccountSid;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "twilio_auth_token")
    private String twilioAuthToken;

    @Column(name = "twilio_whatsapp_number")
    private String twilioWhatsAppNumber;

    @Column(name = "default_language_code", nullable = false)
    private String defaultLanguageCode = "en";

    /** When true (the default), only vendor-registered, non-blocked customers can order over
     * WhatsApp; unknown numbers get a one-time "please register" notice and are otherwise
     * ignored. When false, first contact auto-registers the customer (the original behavior). */
    @Column(name = "require_customer_registration", nullable = false)
    private boolean requireCustomerRegistration = true;

    /**
     * EAGER (unlike everything else in this codebase, which is LAZY by default): this entity is
     * routinely loaded by {@code WhatsAppWebhookService} in a short-lived, non-{@code @Transactional}
     * repository call and handed off to {@code WhatsAppConversationService} already detached — a
     * LAZY collection here would throw {@code LazyInitializationException} on every bot turn.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tenant_language", joinColumns = @JoinColumn(name = "tenant_id"))
    @Column(name = "language_code")
    private Set<String> supportedLanguageCodes = new LinkedHashSet<>(Set.of("en"));

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "INR";

    @Column(nullable = false)
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;
}
