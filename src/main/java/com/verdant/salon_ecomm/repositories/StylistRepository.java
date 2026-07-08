package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.services.StylistsService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface StylistRepository extends JpaRepository<Stylist, UUID>, JpaSpecificationExecutor<Stylist> {
    List<Stylist> findByStatus(String status);
    List<Stylist> findAllByStatus(String status);
}