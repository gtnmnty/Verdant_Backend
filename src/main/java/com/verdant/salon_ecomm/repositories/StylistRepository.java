package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Stylist;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface StylistRepository extends JpaRepository<Stylist, UUID>, JpaSpecificationExecutor<Stylist> {
    // Used to block branch deletion when stylists are still assigned — one IN-clause
    // query for the whole batch instead of looping per branch id.
    @Query("SELECT DISTINCT s.branch.id FROM Stylist s WHERE s.branch.id IN :branchIds")
    List<UUID> findDistinctBranchIdsWithStylists(@Param("branchIds") Collection<UUID> branchIds);

    @Override
    @EntityGraph(attributePaths = {"branch"})
    Page<Stylist> findAll(Specification<Stylist> spec, @NonNull Pageable pageable);
}