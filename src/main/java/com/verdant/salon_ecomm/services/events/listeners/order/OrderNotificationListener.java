package com.verdant.salon_ecomm.services.events.listeners.order;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.services.NotificationService;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    // Staff recipients (receptionist, admin, manager, owner) come from StaffNotifier.STAFF_ROLES (M-38).
    private final NotificationService notificationService;
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = event.order();

        // Confirmation to the customer themselves
        notificationService.create(new NotificationCreateDto(
            event.customer().getId(),
            NotificationType.ORDER_CREATED,
            "Order placed",
            order.getOrderCode() + " placed successfully.",
            ReferenceType.ORDER,
            order.getId(),
            NotificationPriority.INFO,
            null,
            null
        ));

        notifyStaff(order, NotificationType.ORDER_CREATED, "New order placed",
            "Order " + order.getOrderCode() + " placed by " + event.customer().getFullName(),
            null, null);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreatedByAdmin(OrderCreatedByAdminEvent event) {
        Order order = event.order();

        notifyStaff(order, NotificationType.ORDER_CREATED, "Order created",
            "Order " + order.getOrderCode() + " created for " + order.getUser().getFullName(),
            event.actor().getId(), event.actor().getFullName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        Order order = event.order();

        if (event.paymentStatusChanged()) {
            PaymentStatus status = order.getPaymentStatus();

            NotificationType type = switch (status) {
                case PAID -> NotificationType.ORDER_PAYMENT_SUCCESS;
                case FAILED -> NotificationType.ORDER_PAYMENT_FAILED;
                case PENDING -> NotificationType.ORDER_PAYMENT_PENDING;
                case REQUIRES_ACTION -> NotificationType.ORDER_PAYMENT_ACTION_REQUIRED;
                case PROCESSED -> NotificationType.ORDER_PAYMENT_PROCESSING;
                case REFUNDED, PARTIALLY_REFUNDED -> NotificationType.ORDER_REFUNDED; // M-14
                case CANCELLED -> NotificationType.ORDER_PAYMENT_CANCELLED;
            };

            NotificationPriority priority = switch (status) {
                case FAILED, REFUNDED, PARTIALLY_REFUNDED, CANCELLED -> NotificationPriority.CRITICAL;
                case PAID -> NotificationPriority.INFO;
                case PENDING, REQUIRES_ACTION, PROCESSED -> NotificationPriority.WARNING;
            };

            // Payment status matters most to the customer directly.
            notificationService.create(new NotificationCreateDto(
                order.getUser().getId(),
                type,
                "Payment " + status.name().toLowerCase(),
                "Payment for " + order.getOrderCode() + " " + status.name().toLowerCase() + ".",
                ReferenceType.ORDER,
                order.getId(),
                priority,
                null,
                null
            ));
        }

        if (event.orderStatusChanged()) {
            notificationService.create(new NotificationCreateDto(
                order.getUser().getId(),
                NotificationType.ORDER_STATUS_CHANGED,
                "Order status updated",
                order.getOrderCode() + " is now " + order.getOrderStatus().name().toLowerCase() + ".",
                ReferenceType.ORDER,
                order.getId(),
                NotificationPriority.INFO,
                null,
                null
            ));

            notifyStaff(order, NotificationType.ORDER_STATUS_CHANGED, "Order status updated",
                "Order " + order.getOrderCode() + " status changed to " + order.getOrderStatus(),
                event.actor().getId(), event.actor().getFullName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdersDeleted(OrdersDeletedEvent event) {
        if (event.orders().isEmpty()) return;

        UUID actorId = event.actor() != null ? event.actor().getId() : null;
        String actorName = event.actor() != null ? event.actor().getFullName() : null;

        for (Order order : event.orders()) {
            notificationService.create(new NotificationCreateDto(
                order.getUser().getId(),
                NotificationType.ORDER_DELETED,
                "Order removed",
                order.getOrderCode() + " was removed by staff.",
                ReferenceType.ORDER,
                order.getId(),
                NotificationPriority.WARNING,
                actorId,
                actorName
            ));
        }

        notifyStaff(event.orders().getFirst(), NotificationType.BULK_ACTION_PERFORMED, "Bulk order deletion",
            event.orders().size() + " orders were deleted" + (actorName != null ? " by " + actorName : "") + ".",
            actorId, actorName);
    }

    // ── Helpers ──────────────────────────────────────────

    // M-38: delegates to StaffNotifier. This copy had no per-recipient try/catch and notified suspended staff.
    private void notifyStaff(
        Order order, NotificationType type, String title, String message,
        UUID actorId, String actorName
    ) {
        staffNotifier.notify(ReferenceType.ORDER, order.getId(), type, title, message, actorId, actorName);
    }
}
