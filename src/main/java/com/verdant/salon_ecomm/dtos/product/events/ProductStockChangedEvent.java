package com.verdant.salon_ecomm.dtos.product.events;

import com.verdant.salon_ecomm.models.entities.User;

import java.util.UUID;

// M-29: stock moved through an order (OrderService.reserveStock / restoreStock, i.e. the atomic
// decrementStock / incrementStock queries). Those never went through ProductUpdatedEvent, so the
// PRODUCT_LOW_STOCK / OUT_OF_STOCK / BACK_IN_STOCK alerts never fired for sales.
// Plain values, read right after the UPDATE in the same transaction, so the AFTER_COMMIT listener doesn't
// touch a lazy or stale Product entity. actor is null for customer checkouts.
// Notification only (ProductNotificationListener): the order's own audit entries already record the sale.
public record ProductStockChangedEvent(
    UUID productId,
    String productName,
    int previousStock,
    int newStock,
    int lowStockThreshold,
    User actor
) {}
