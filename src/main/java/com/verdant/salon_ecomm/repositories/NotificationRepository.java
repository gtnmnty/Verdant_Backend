package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, UUID>, JpaSpecificationExecutor<Notification> {

    Page<Notification> findAll(Specification<Notification> spec, Pageable pageable);

    long countByUser_IdAndIsReadFalse(UUID userId);

    List<Notification> findByIdInAndUser_Id(List<UUID> ids, UUID userId);

    // ── Bulk mark-as-read ─────────────────────────────────
    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.id IN :ids AND n.user.id = :userId
            """)
    int markAsReadByIdsAndUser(
            @Param("ids") List<UUID> ids,
            @Param("userId") UUID userId,
            @Param("readAt") OffsetDateTime readAt
    );

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.isRead = true, n.readAt = :readAt
            WHERE n.user.id = :userId AND n.isRead = false
            """)
    int markAllAsReadByUser(
            @Param("userId") UUID userId,
            @Param("readAt") OffsetDateTime readAt
    );

    // ── Bulk delete, scoped to owner so a user can't delete another's notif ──
    long deleteByIdInAndUser_Id(List<UUID> ids, UUID userId);
}
