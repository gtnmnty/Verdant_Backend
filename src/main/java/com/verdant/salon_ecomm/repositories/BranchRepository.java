package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID>, JpaSpecificationExecutor<Branch> {
    List<Branch> findAllByOrderByNameAsc();
    Optional<Branch> findByName(String name);
}
