package com.verdant.salon_ecomm.dtos.product.events;

import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;

import java.util.List;

public record ProductsBulkDeletedEvent(List<Product> products, User actor) {
}
