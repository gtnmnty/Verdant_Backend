package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.entities.AuditLog;
import com.verdant.salon_ecomm.dtos.AuditLogDto;
import org.springframework.stereotype.Component;

@Component
public class AuditLogMapper {

    public AuditLogDto toDto(AuditLog log) {
        return new AuditLogDto(
            log.getId(),
            log.getActionType(),
            log.getTitle(),
            log.getDetail(),
            log.isSelfService() ? "Self-service" : log.getActorLabel(),
            log.isSelfService(),
            log.getCreatedAt()
        );
    }
}
