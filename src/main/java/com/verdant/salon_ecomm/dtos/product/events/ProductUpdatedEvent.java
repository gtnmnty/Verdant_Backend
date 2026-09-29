package com.verdant.salon_ecomm.dtos.product.events;

import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;

public record ProductUpdatedEvent(
    Product product,
    User actor,
    boolean stockChanged,
    int previousStockQuantity,
    boolean statusChanged,
    CollectionStatus previousStatus
) {}
