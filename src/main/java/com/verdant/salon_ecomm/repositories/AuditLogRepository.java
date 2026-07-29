package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.AuditLog;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        AuditEntityType entityType, String entityId, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

