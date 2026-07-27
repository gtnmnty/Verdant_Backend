package com.verdant.salon_ecomm.dtos;

import com.verdant.salon_ecomm.models.enums.audit.AuditActionType;

public record AuditLogDto(
    Long id,
    AuditActionType actionType,
    String title,
    String detail,
    String actorLabel,
    boolean selfService,
    String createdAt
) {}
