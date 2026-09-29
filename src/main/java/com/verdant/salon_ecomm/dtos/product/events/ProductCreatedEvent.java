package com.verdant.salon_ecomm.dtos.product.events;

import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;

public record ProductCreatedEvent(
    Product product,
    User actor
) {}
