package com.verdant.salon_ecomm.services.events.listeners.product;

import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.dtos.product.events.ProductCreatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductDeletedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductStockChangedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductUpdatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductsBulkDeletedEvent;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.utils.StaffNotifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductNotificationListener {

    // M-38: staff fan-out goes through StaffNotifier (active staff only, one transaction, failures isolated).
    private final StaffNotifier staffNotifier;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        Product product = event.product();

        notifyStaff(product.getId(), NotificationType.PRODUCT_ADDED, "Product added",
            product.getName() + " was added to the catalog",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdated(ProductUpdatedEvent event) {
        Product product = event.product();

        if (event.stockChanged()) {
            alertOnStockChange(product.getId(), product.getName(), event.previousStockQuantity(),
                product.getStockQuantity(), product.getLowStockThreshold(), event.actor());
        }

        if (event.statusChanged() || (!event.stockChanged())) {
            notifyStaff(product.getId(), NotificationType.PRODUCT_UPDATED, "Product updated",
                product.getName() + " was updated",
                event.actor());
        }
    }

    // M-29: stock moved by an order (checkout, admin order edit, cancel/expiry restock).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductStockChanged(ProductStockChangedEvent event) {
        alertOnStockChange(event.productId(), event.productName(), event.previousStock(),
            event.newStock(), event.lowStockThreshold(), event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeletedEvent event) {
        Product product = event.product();

        notifyStaff(product.getId(), NotificationType.PRODUCT_DELETED, "Product deleted",
            product.getName() + " was removed from the catalog",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductImageUpdated(ProductImageUpdatedEvent event) {
        Product product = event.product();

        notifyStaff(product.getId(), NotificationType.PRODUCT_IMAGE_UPDATED, "Product image updated",
            "Primary image for \"" + product.getName() + "\" was changed.",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductsBulkDeleted(ProductsBulkDeletedEvent event) {
        if (event.products().isEmpty()) return;

        notifyStaff(event.products().getFirst().getId(), NotificationType.BULK_ACTION_PERFORMED, "Bulk product deletion",
            event.products().size() + " products were deleted"
                + (event.actor() != null ? " by " + event.actor().getFullName() : "") + ".",
            event.actor());
    }

    // ── Helpers ──────────────────────────────────────────

    // M-29: alerts fire when the stock CROSSES a line, not on every change. Every sale now reaches this
    // method, so "fire while below the threshold" would send a low-stock alert per order.
    //   previous > 0  -> now 0               : out of stock
    //   previous <= 0 -> now above 0         : back in stock
    //   previous > threshold -> now at/below : low on stock
    private void alertOnStockChange(UUID productId, String name, int previous, int current, int threshold, User actor) {
        if (current == previous) return;
        if (current <= 0 && previous > 0) {
            notifyStaff(productId, NotificationType.PRODUCT_OUT_OF_STOCK, "Product out of stock",
                name + " is now out of stock", actor);
        } else if (current > 0 && previous <= 0) {
            notifyStaff(productId, NotificationType.PRODUCT_BACK_IN_STOCK, "Product back in stock",
                name + " is back in stock", actor);
        } else if (current <= threshold && previous > threshold) {
            notifyStaff(productId, NotificationType.PRODUCT_LOW_STOCK, "Product low on stock",
                name + " has " + current + " left", actor);
        }
    }

    private void notifyStaff(UUID productId, NotificationType type, String title, String message, User actor) {
        staffNotifier.notify(ReferenceType.PRODUCT, productId, type, title, message, actor);
    }
}
