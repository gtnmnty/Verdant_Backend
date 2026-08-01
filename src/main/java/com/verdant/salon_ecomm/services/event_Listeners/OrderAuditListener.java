package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.entities.Order;
import com.verdant.salon_ecomm.dtos.order.events.OrderCreatedByAdminEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderPlacedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrderUpdatedEvent;
import com.verdant.salon_ecomm.dtos.order.events.OrdersDeletedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderAuditListener {

    private final AuditLogService auditLogService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        Order order = event.order();
        auditLogService.recordSelfService(
            AuditEntityType.ORDER,
            order.getId(),
            AuditActionType.CREATED,
            "Order " + order.getOrderCode() + " placed",
            "Placed by customer " + event.customer().getFullName()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreatedByAdmin(OrderCreatedByAdminEvent event) {
        Order order = event.order();
        auditLogService.record(
            AuditEntityType.ORDER,
            order.getId(),
            AuditActionType.CREATED,
            "Order " + order.getOrderCode() + " created",
            "Created on behalf of " + order.getUser().getFullName(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdated(OrderUpdatedEvent event) {
        Order order = event.order();

        if (event.orderStatusChanged()) {
            auditLogService.record(
                AuditEntityType.ORDER,
                order.getId(),
                AuditActionType.UPDATED,
                "Order " + order.getOrderCode() + " status changed",
                event.previousOrderStatus() + " -> " + order.getOrderStatus(),
                event.actor()
            );
        }

        if (event.paymentStatusChanged()) {
            auditLogService.record(
                AuditEntityType.ORDER,
                order.getId(),
                AuditActionType.UPDATED,
                "Order " + order.getOrderCode() + " payment status changed",
                event.previousPaymentStatus() + " -> " + order.getPaymentStatus(),
                event.actor()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrdersDeleted(OrdersDeletedEvent event) {
        if (event.orders().isEmpty()) return;
        int count = event.orders().size();
        auditLogService.record(
            AuditEntityType.ORDER,
            event.orders().getFirst().getId(),
            AuditActionType.BULK_DELETED,
            count + " orders deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
