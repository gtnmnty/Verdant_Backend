package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.AuditLog;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        AuditEntityType entityType, String entityId, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // M-39: replaces every occurrence of a deleted user's email in audit titles and details (older entries
    // stored addresses in plain text, see M-22). Exact, case-sensitive match on the stored address: emails
    // are stored lower-case since M-36, and audit text was built from the stored value.
    @Modifying
    @Query(value = """
        UPDATE audit_logs
        SET title = replace(title, :email, :replacement),
            detail = replace(detail, :email, :replacement)
        WHERE strpos(title, :email) > 0 OR strpos(detail, :email) > 0
        """, nativeQuery = true)
    int redactEmail(@Param("email") String email, @Param("replacement") String replacement);
}

