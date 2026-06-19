package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Stylist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StylistRepository extends JpaRepository<Stylist, UUID> {
    List<Stylist> findByStatus(String status);
}