package com.verdant.salon_ecomm.dtos.product;

import java.util.UUID;

// M-29: a product's stock as stored right now, read with a JPQL constructor query right after the atomic
// stock UPDATE (same transaction, so it includes that change). Used to build ProductStockChangedEvent
// without touching the Product entity, which may be stale in the persistence context.
public record ProductStockLevel(UUID id, String name, Integer stockQuantity, int lowStockThreshold) {
}
