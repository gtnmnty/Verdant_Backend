package com.verdant.salon_ecomm.services.event_Listeners;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.dtos.product.events.ProductCreatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductDeletedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductUpdatedEvent;
import com.verdant.salon_ecomm.dtos.product.events.ProductsBulkDeletedEvent;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
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
public class ProductNotificationListener {

    // Staff who should hear about catalog/stock changes.
    private static final List<AccountRole> STAFF_ROLES = List.of(
        AccountRole.RECEPTIONIST, AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER
    );

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        Product product = event.product();

        notifyStaff(product, NotificationType.PRODUCT_ADDED, "Product added",
            product.getName() + " was added to the catalog",
            event.actor());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdated(ProductUpdatedEvent event) {
        Product product = event.product();

        if (event.stockChanged()) {
            if (product.getStockQuantity() == 0) {
                notifyStaff(product, NotificationType.PRODUCT_OUT_OF_STOCK, "Product out of stock",
                    product.getName() + " is now out of stock",
                    event.actor());
            } else if (product.getStockQuantity() <= product.getLowStockThreshold()) {
                notifyStaff(product, NotificationType.PRODUCT_LOW_STOCK, "Product low on stock",
                    product.getName() + " has " + product.getStockQuantity() + " left",
                    event.actor());
            } else if (event.previousStockQuantity() == 0 && product.getStockQuantity() > 0) {
                notifyStaff(product, NotificationType.PRODUCT_BACK_IN_STOCK, "Product back in stock",
                    product.getName() + " is back in stock",
                    event.actor());
            }
        }

        if (event.statusChanged() || (!event.stockChanged())) {
            notifyStaff(product, NotificationType.PRODUCT_UPDATED, "Product updated",
                product.getName() + " was updated",
                event.actor());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeletedEvent event) {
        Product product = event.product();

        notifyStaff(product, NotificationType.PRODUCT_DELETED, "Product deleted",
            product.getName() + " was removed from the catalog",
            event.actor());
    }

    // ── Helpers ──────────────────────────────────────────

    private void notifyStaff(Product product, NotificationType type, String title, String message, User actor) {
        List<User> staff = userRepository.findByRoleIn(STAFF_ROLES);
        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null ? actor.getFullName() : "System";

        for (User staffMember : staff) {
            notificationService.create(new NotificationCreateDto(
                staffMember.getId(),
                type,
                title,
                message,
                ReferenceType.PRODUCT,
                product.getId(),
                NotificationPriority.INFO,
                actorId,
                actorName
            ));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductsBulkDeleted(ProductsBulkDeletedEvent event) {
        if (event.products().isEmpty()) return;

        notifyStaff(event.products().get(0), NotificationType.BULK_ACTION_PERFORMED, "Bulk product deletion",
            event.products().size() + " products were deleted"
                + (event.actor() != null ? " by " + event.actor().getFullName() : "") + ".",
            event.actor());
    }
}
