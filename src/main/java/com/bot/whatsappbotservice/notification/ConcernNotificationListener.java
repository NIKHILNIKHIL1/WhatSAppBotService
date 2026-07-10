package com.bot.whatsappbotservice.notification;

import com.bot.whatsappbotservice.order.event.ConcernResolvedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Same decoupling as the status-change and payment listeners: the order module resolves a
 * concern without knowing that this closes the loop with the customer on WhatsApp. */
@Component
public class ConcernNotificationListener {

    private final NotificationService notificationService;

    public ConcernNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConcernResolved(ConcernResolvedEvent event) {
        notificationService.notifyConcernResolved(event.tenantId(), event.concernId());
    }
}
