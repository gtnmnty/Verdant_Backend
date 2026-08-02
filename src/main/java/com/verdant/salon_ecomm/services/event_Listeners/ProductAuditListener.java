package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.dtos.product.events.ProductCreatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductDeletedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductUpdatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductsBulkDeletedEvent;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductAuditListener {

    private final AuditLogService auditLogService;

    public ProductAuditListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        Product product = event.product();
        auditLogService.record(
            AuditEntityType.PRODUCT,
            product.getId(),
            AuditActionType.CREATED,
            "Product " + product.getName() + " created",
            "SKU " + product.getSku(),
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdated(ProductUpdatedEvent event) {
        Product product = event.product();

        if (event.stockChanged()) {
            auditLogService.record(
                AuditEntityType.PRODUCT,
                product.getId(),
                AuditActionType.UPDATED,
                "Product " + product.getName() + " stock changed",
                event.previousStockQuantity() + " -> " + product.getStockQuantity(),
                event.actor()
            );
        }

        if (event.statusChanged()) {
            auditLogService.record(
                AuditEntityType.PRODUCT,
                product.getId(),
                AuditActionType.UPDATED,
                "Product " + product.getName() + " status changed",
                event.previousStatus() + " -> " + product.getStatus(),
                event.actor()
            );
        }

        if (!event.stockChanged() && !event.statusChanged()) {
            auditLogService.record(
                AuditEntityType.PRODUCT,
                product.getId(),
                AuditActionType.UPDATED,
                "Product " + product.getName() + " updated",
                null,
                event.actor()
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeletedEvent event) {
        Product product = event.product();
        auditLogService.record(
            AuditEntityType.PRODUCT,
            product.getId(),
            AuditActionType.DELETED,
            "Product " + product.getName() + " deleted",
            null,
            event.actor()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductsBulkDeleted(ProductsBulkDeletedEvent event) {
        if (event.products().isEmpty()) return;
        int count = event.products().size();
        auditLogService.record(
            AuditEntityType.PRODUCT,
            event.products().getFirst().getId(),
            AuditActionType.BULK_DELETED,
            count + " products deleted",
            "Bulk deleted by staff",
            event.actor()
        );
    }
}
