package com.bot.whatsappbotservice.notification;

import com.bot.whatsappbotservice.common.TenantContext;
import com.bot.whatsappbotservice.customer.Customer;
import com.bot.whatsappbotservice.i18n.WhatsAppMessages;
import com.bot.whatsappbotservice.order.OrderHeader;
import com.bot.whatsappbotservice.order.OrderItem;
import com.bot.whatsappbotservice.order.OrderRepository;
import com.bot.whatsappbotservice.order.OrderStatus;
import com.bot.whatsappbotservice.tenant.Tenant;
import com.bot.whatsappbotservice.tenant.TenantRepository;
import com.bot.whatsappbotservice.whatsapp.MessageStatus;
import com.bot.whatsappbotservice.whatsapp.WhatsAppMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final OrderRepository orderRepository;
    private final TenantRepository tenantRepository;
    private final WhatsAppMessagingService whatsAppMessagingService;
    private final WhatsAppMessages messages;

    public NotificationService(NotificationRepository notificationRepository, OrderRepository orderRepository,
                                TenantRepository tenantRepository, WhatsAppMessagingService whatsAppMessagingService,
                                WhatsAppMessages messages) {
        this.notificationRepository = notificationRepository;
        this.orderRepository = orderRepository;
        this.tenantRepository = tenantRepository;
        this.whatsAppMessagingService = whatsAppMessagingService;
        this.messages = messages;
    }

    /** Fires only on the transition to fully PAID (see {@link PaymentNotificationListener}).
     * Localized to the customer's preferred language — unlike the older status-change message
     * above, which predates the i18n bundle and is still English-only. */
    @Transactional
    public void notifyPaymentReceived(Long tenantId, Long orderId) {
        TenantContext.setTenantId(tenantId);

        OrderHeader order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found when sending payment-received notification", orderId);
            return;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            log.warn("Tenant {} not found when sending payment-received notification", tenantId);
            return;
        }
        Customer customer = order.getCustomer();
        String lang = customer.getPreferredLanguageCode() != null
                ? customer.getPreferredLanguageCode()
                : tenant.getDefaultLanguageCode();

        Notification notification = new Notification();
        notification.setRecipientType(RecipientType.CUSTOMER);
        notification.setRecipientId(customer.getId());
        notification.setChannel(NotificationChannel.WHATSAPP);
        notification.setTemplateCode("PAYMENT_RECEIVED");
        notification.setOrder(order);
        notification.setStatus(NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        String message = messages.get("bot.payment.received", lang,
                String.valueOf(order.getAmountPaid()), order.getCurrencyCode(), order.getOrderNumber());
        MessageStatus sendResult = whatsAppMessagingService.sendText(tenant, customer, customer.getPhoneNumber(), message);

        notification.setStatus(sendResult == MessageStatus.SENT ? NotificationStatus.SENT : NotificationStatus.FAILED);
        if (sendResult == MessageStatus.SENT) {
            notification.setSentAt(java.time.Instant.now());
        }
        notificationRepository.save(notification);
    }

    @Transactional
    public void notifyOrderStatusChange(Long tenantId, Long orderId, OrderStatus fromStatus, OrderStatus toStatus) {
        // Defensive: this listener fires synchronously on the same thread as the original
        // request (already tenant-scoped correctly), but the tenant id is passed explicitly in
        // the event rather than relied upon, so this method doesn't depend on thread-context timing.
        TenantContext.setTenantId(tenantId);

        OrderHeader order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found when sending status-change notification", orderId);
            return;
        }
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null || tenant.getWhatsappPhoneNumberId() == null || tenant.getWhatsappAccessToken() == null) {
            log.info("Tenant {} has no WhatsApp configured; skipping order status notification", tenantId);
            return;
        }
        Customer customer = order.getCustomer();

        Notification notification = new Notification();
        notification.setRecipientType(RecipientType.CUSTOMER);
        notification.setRecipientId(customer.getId());
        notification.setChannel(NotificationChannel.WHATSAPP);
        notification.setTemplateCode("ORDER_STATUS_CHANGED");
        notification.setOrder(order);
        notification.setStatus(NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        String message = "Your order " + order.getOrderNumber() + " status is now: " + toStatus
                + (fromStatus != null ? " (was: " + fromStatus + ")" : "");
        MessageStatus sendResult = whatsAppMessagingService.sendText(tenant, customer, customer.getPhoneNumber(), message);

        notification.setStatus(sendResult == MessageStatus.SENT ? NotificationStatus.SENT : NotificationStatus.FAILED);
        if (sendResult == MessageStatus.SENT) {
            notification.setSentAt(java.time.Instant.now());
        }
        notificationRepository.save(notification);

        if (toStatus == OrderStatus.CONFIRMED) {
            try {
                notifyVendorOfNewOrder(tenant, order, customer);
            } catch (Exception e) {
                log.error("Vendor notification failed for order {} (tenant {}); customer notification is unaffected",
                        order.getId(), tenant.getId(), e);
            }
        }
    }

    private void notifyVendorOfNewOrder(Tenant tenant, OrderHeader order, Customer customer) {
        String vendorPhoneNumber = tenant.getVendorNotificationPhoneNumber();
        if (vendorPhoneNumber == null || vendorPhoneNumber.isBlank()) {
            log.info("Tenant {} has no vendor notification number configured; skipping vendor order alert",
                    tenant.getId());
            return;
        }

        Notification notification = new Notification();
        notification.setRecipientType(RecipientType.VENDOR);
        notification.setRecipientId(tenant.getId());
        notification.setChannel(NotificationChannel.WHATSAPP);
        notification.setTemplateCode("VENDOR_ORDER_CONFIRMED");
        notification.setOrder(order);
        notification.setStatus(NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        String message = buildVendorOrderMessage(order, customer);
        MessageStatus sendResult = whatsAppMessagingService.sendText(tenant, customer, vendorPhoneNumber, message);

        notification.setStatus(sendResult == MessageStatus.SENT ? NotificationStatus.SENT : NotificationStatus.FAILED);
        if (sendResult == MessageStatus.SENT) {
            notification.setSentAt(java.time.Instant.now());
        }
        notificationRepository.save(notification);
    }

    private String buildVendorOrderMessage(OrderHeader order, Customer customer) {
        StringBuilder message = new StringBuilder("Order confirmed and ready for processing: ")
                .append(order.getOrderNumber())
                .append("\nCustomer: ")
                .append(customer.getFullName() != null ? customer.getFullName() : customer.getPhoneNumber())
                .append(" (")
                .append(customer.getPhoneNumber())
                .append(")\n\nItems:\n");
        for (OrderItem item : order.getItems()) {
            message.append("- ")
                    .append(item.getProductNameSnapshot())
                    .append(" x")
                    .append(item.getQuantity())
                    .append(" = ")
                    .append(item.getLineTotal())
                    .append('\n');
        }
        message.append("\nTotal: ").append(order.getCurrencyCode()).append(' ').append(order.getTotalAmount());
        return message.toString();
    }
}
