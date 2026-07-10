package com.bot.whatsappbotservice.notification;

import com.bot.whatsappbotservice.order.PaymentStatus;
import com.bot.whatsappbotservice.order.event.PaymentRecordedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Same decoupling as {@link OrderStatusNotificationListener}: the order module records payments
 * without knowing that full payment triggers a customer message. Partial payments deliberately
 * don't notify — a khata-style customer settling in instalments doesn't need a message per
 * instalment, only the final "settled" confirmation.
 */
@Component
public class PaymentNotificationListener {

    private final NotificationService notificationService;

    public PaymentNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentRecorded(PaymentRecordedEvent event) {
        if (event.newStatus() == PaymentStatus.PAID) {
            notificationService.notifyPaymentReceived(event.tenantId(), event.orderId());
        }
    }
}
