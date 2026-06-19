package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.SalonService;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalonServiceRepository extends JpaRepository<SalonService, UUID> {
    List<SalonService> findByCategory(String category);
    List<SalonService> findByIsFeaturedTrue();
}