package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.payment.OrderPaymentDisputedEvent;
import com.verdant.salon_ecomm.dtos.payment.PaymentStatusChangedEvent;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
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
        AuditActionType action;

        if (event.previousStatus() == null && event.newStatus() == PaymentStatus.PENDING) {
            action = AuditActionType.PAYMENT_INITIATED;
        } else {
            action = switch (event.newStatus()) {
                case PAID -> AuditActionType.PAYMENT_SUCCEEDED;
                case FAILED -> AuditActionType.PAYMENT_FAILED;
                case REFUNDED, PARTIALLY_REFUNDED -> AuditActionType.PAYMENT_REFUNDED;
                default -> AuditActionType.UPDATED;
            };
        }

        String title = "Payment " + event.newStatus().name().toLowerCase();
        String detail = "Stripe event: " + event.stripeEventType();

        // Only creating the intent is the customer's own action; every later status change
        // arrives from a Stripe webhook, so it's recorded as System.
        if (action == AuditActionType.PAYMENT_INITIATED) {
            auditLogService.recordSelfService(AuditEntityType.ORDER, event.orderId(), action, title, detail, event.userId());
        } else {
            auditLogService.record(AuditEntityType.ORDER, event.orderId(), action, title, detail, null);
        }
    }

    // M-13: a chargeback opened on an order. The payment status is unchanged (the dispute may be won),
    // so without this entry nobody in the admin would know. SECURITY so it stands out in the audit log.
    // Staff respond with evidence in the Stripe Dashboard; a lost dispute later marks the order REFUNDED.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void onOrderPaymentDisputed(OrderPaymentDisputedEvent event) {
        String amount = event.amount() != null ? event.amount().toPlainString() : "unknown amount";
        auditLogService.record(
            AuditEntityType.ORDER, event.orderId(), AuditActionType.SECURITY,
            "Payment disputed (chargeback) on " + event.orderCode(),
            "Stripe dispute opened for " + amount + " (status " + event.disputeStatus()
                + "). Respond in the Stripe Dashboard before the deadline.",
            null
        );
    }
}