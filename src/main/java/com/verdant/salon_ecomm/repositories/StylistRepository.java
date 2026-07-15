package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Stylist;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface StylistRepository extends JpaRepository<Stylist, UUID>, JpaSpecificationExecutor<Stylist> {
    List<Stylist> findByStatus(String status);
    List<Stylist> findAllByStatus(String status);


    @Override
    @EntityGraph(attributePaths = {"branch"})
    Page<Stylist> findAll(Specification<Stylist> spec, @NonNull Pageable pageable);
}