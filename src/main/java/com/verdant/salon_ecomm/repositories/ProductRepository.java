package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByItemCatalog(ItemCatalog category);
    List<Product> findByIsFeaturedTrue();
    List<Product> findByIsActive(Boolean isActive);
}
