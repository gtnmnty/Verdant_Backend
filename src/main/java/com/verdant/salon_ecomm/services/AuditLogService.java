package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.entities.AuditLog;
import com.verdant.salon_ecomm.dtos.AuditLogDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.mappers.AuditLogMapper;
import com.verdant.salon_ecomm.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    // ---------- Queries ----------

    public Page<AuditLogDto> getForEntity(AuditEntityType entityType, String entityId, Pageable pageable) {
        return auditLogRepository
            .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
            .map(auditLogMapper::toDto);
    }

    public Page<AuditLogDto> getDashboardFeed(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(auditLogMapper::toDto);
    }

    // ---------- Mutations ----------

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
        AuditEntityType entityType, UUID entityId, AuditActionType actionType,
        String title, String detail, User actor
    ) {
        AuditLog log = AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId.toString())
            .actionType(actionType)
            .title(title)
            .detail(detail)
            .actor(actor)
            .actorLabel(actor != null ? resolveActorLabel(actor) : "System")
            .selfService(false)
            .build();
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSelfService(
        AuditEntityType entityType, UUID entityId, AuditActionType actionType,
        String title, String detail
    ) {
        AuditLog log = AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId.toString())
            .actionType(actionType)
            .title(title)
            .detail(detail)
            .selfService(true)
            .build();
        auditLogRepository.save(log);
    }

    // ---------- Private helpers ----------

    private String resolveActorLabel(User actor) {
        String role = actor.getRole() != null ? capitalize(actor.getRole().name()) : "Staff";
        return role + " " + actor.getFullName();
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1).toLowerCase();
    }
}

