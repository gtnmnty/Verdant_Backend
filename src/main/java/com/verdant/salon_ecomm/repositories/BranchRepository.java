package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findAllByOrderByNameAsc();
    Optional<Branch> findByName(String name);
}

