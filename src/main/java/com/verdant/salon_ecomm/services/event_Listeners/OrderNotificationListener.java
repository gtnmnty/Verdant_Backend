package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.models.enums.PaymentStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    // Every non-customer role that should hear about new/changed orders,
    // matching the "receptionist, admin, manager, owner" notification spec.
    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

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
                case REFUNDED -> NotificationType.ORDER_REFUNDED;
                case CANCELLED -> NotificationType.ORDER_PAYMENT_CANCELLED;
            };

            NotificationPriority priority = switch (status) {
                case FAILED -> NotificationPriority.CRITICAL;
                case PAID -> NotificationPriority.INFO;
                case PENDING, REQUIRES_ACTION, PROCESSED -> NotificationPriority.WARNING;
                case REFUNDED, CANCELLED -> NotificationPriority.WARNING;
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
        for (Order order : event.orders()) {
            notificationService.create(new NotificationCreateDto(
                order.getUser().getId(),
                NotificationType.ORDER_DELETED,
                "Order removed",
                order.getOrderCode() + " was removed by staff.",
                ReferenceType.ORDER,
                order.getId(),
                NotificationPriority.WARNING,
                event.actor().getId(),
                event.actor().getFullName()
            ));

            notifyStaff(order, NotificationType.ORDER_DELETED, "Order deleted",
                "Order " + order.getOrderCode() + " deleted by " + event.actor().getFullName(),
                event.actor().getId(), event.actor().getFullName());
        }
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(
        Order order, NotificationType type, String title, String message,
        UUID actorId, String actorName
    ) {
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                type,
                title,
                message,
                ReferenceType.ORDER,
                order.getId(),
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }
}
