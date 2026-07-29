package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PaymentAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onPaymentStatusChanged(PaymentStatusChangedEvent event) {
        AuditActionType action = switch (event.newStatus()) {
            case PAID -> AuditActionType.PAYMENT_SUCCEEDED;
            case FAILED -> AuditActionType.PAYMENT_FAILED;
            case REFUNDED -> AuditActionType.PAYMENT_REFUNDED;
            default -> AuditActionType.UPDATED;
        };

        auditLogService.recordSelfService(
            AuditEntityType.ORDER,
            event.orderId(), // adjust if entityId is UUID vs Long
            action,
            "Payment " + event.newStatus().name().toLowerCase(),
            "Stripe event: " + event.stripeEventType()
        );
    }
}
