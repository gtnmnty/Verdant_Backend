package com.verdant.salon_ecomm.dtos.audit;

import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;

import java.time.Instant;

public record AuditLogDto(
    Long id,
    AuditActionType actionType,
    String title,
    String detail,
    String actorLabel,
    boolean selfService,
    Instant createdAt
) {}
