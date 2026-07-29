package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        NotificationType type = switch (event.newStatus()) {
            case PAID -> NotificationType.ORDER_PAYMENT_SUCCESS;
            case FAILED -> NotificationType.ORDER_PAYMENT_FAILED;
            case REFUNDED -> NotificationType.ORDER_REFUNDED;
            default -> null;
        };
        if (type == null) return;

        notificationService.create(new NotificationCreateDto(
            event.userId(),
            type,
            "Payment " + event.newStatus().name().toLowerCase(),
            "Your order payment status changed to " + event.newStatus(),
            ReferenceType.ORDER,
            event.orderId(),
            NotificationPriority.HIGH,
            null,
            "Stripe"
        ));
    }
}
