package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    List<Product> findByCategory(String category);
    List<Product> findByIsFeaturedTrue();
    List<Product> findByStatus(String status);
}
