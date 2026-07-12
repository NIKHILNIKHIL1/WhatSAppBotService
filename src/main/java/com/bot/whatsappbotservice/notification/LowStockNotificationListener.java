package com.bot.whatsappbotservice.notification;

import com.bot.whatsappbotservice.inventory.event.LowStockEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Decouples inventory from notification/whatsapp the same way {@link OrderStatusNotificationListener}
 * decouples orders: {@code InventoryService} publishes {@link LowStockEvent} and knows nothing
 * about messaging. AFTER_COMMIT means an adjustment that rolls back (e.g. an order that fails on a
 * later line item) never produces a false low-stock alarm.
 */
@Component
public class LowStockNotificationListener {

    private final NotificationService notificationService;

    public LowStockNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        notificationService.notifyLowStock(event.tenantId(), event.productId());
    }
}
