package com.bot.whatsappbotservice.notification;

import com.bot.whatsappbotservice.order.event.OrderStatusChangedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Decouples the order module from the notification/whatsapp modules: {@code OrderService}
 * publishes {@link OrderStatusChangedEvent} and knows nothing about how (or whether) that
 * translates into a customer message. Listening AFTER_COMMIT means a status change that gets
 * rolled back never triggers a notification for something that didn't actually happen.
 */
@Component
public class OrderStatusNotificationListener {

    private final NotificationService notificationService;

    public OrderStatusNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        notificationService.notifyOrderStatusChange(
                event.tenantId(), event.orderId(), event.fromStatus(), event.toStatus());
    }
}
