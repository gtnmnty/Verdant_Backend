package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.audit.AuditLogDto;
import com.verdant.salon_ecomm.dtos.audit.AuditLogPage;
import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.models.entities.AuditLog;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.mappers.AuditLogMapper;
import com.verdant.salon_ecomm.repositories.AuditLogRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.utils.TextLimits;
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
    private final UserRepository userRepository;

    // M-33: an over-long title or detail used to fail the INSERT, and the listener swallowed the error, so
    // the audit row was lost. Now the text is cut to fit (marked with "..."). title and actor_label are the
    // default varchar(255); detail is text since V11, capped so one entry stays readable.
    private static final int TITLE_MAX = 255;
    private static final int DETAIL_MAX = 4000;

    // ---------- Queries ----------

    public AuditLogPage getForEntity(AuditEntityType entityType, String entityId, Pageable pageable) {
        Page<AuditLogDto> page = auditLogRepository
            .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, pageable)
            .map(auditLogMapper::toDto);

        return new AuditLogPage(page.getContent(), Math.toIntExact(page.getTotalElements()), page.hasNext());
    }

    public AuditLogPage getDashboardFeed(Pageable pageable) {
        Page<AuditLogDto> page = auditLogRepository
            .findAllByOrderByCreatedAtDesc(pageable)
            .map(auditLogMapper::toDto);

        return new AuditLogPage(page.getContent(), (int) page.getTotalElements(), page.hasNext());
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
            .title(TextLimits.truncate(title, TITLE_MAX))
            .detail(TextLimits.truncate(detail, DETAIL_MAX))
            .actor(actor)
            .actorLabel(TextLimits.truncate(actor != null ? resolveActorLabel(actor) : "System", TITLE_MAX))
            .selfService(false)
            .build();
        auditLogRepository.save(log);
    }

    // Something a user did to their own data (booked, reviewed, changed their password, ...); actor is that user.
    // Webhook and scheduled-job events are not self-service: record them with record(..., null) ("System").
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSelfService(
        AuditEntityType entityType, UUID entityId, AuditActionType actionType,
        String title, String detail, User actor
    ) {
        recordSelfService(entityType, entityId, actionType, title, detail, actor != null ? actor.getId() : null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSelfService(
        AuditEntityType entityType, UUID entityId, AuditActionType actionType,
        String title, String detail, UUID actorId
    ) {
        // Listeners call this after the original transaction closed, so the caller's User can be an
        // uninitialized proxy. Load the actor here before reading its name for the label.
        User managedActor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        AuditLog log = AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId.toString())
            .actionType(actionType)
            .title(TextLimits.truncate(title, TITLE_MAX))
            .detail(TextLimits.truncate(detail, DETAIL_MAX))
            .actor(managedActor)
            .actorLabel(TextLimits.truncate(managedActor != null ? resolveActorLabel(managedActor) : null, TITLE_MAX))
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

