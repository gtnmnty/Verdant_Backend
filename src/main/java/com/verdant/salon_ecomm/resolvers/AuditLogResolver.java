package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.AuditLogDto;
import com.verdant.salon_ecomm.models.enums.audit.AuditEntityType;
import com.verdant.salon_ecomm.services.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class AuditLogResolver {

    private final AuditLogService auditLogService;

    // Global (not branch-scoped) — visible to Owner, Manager, Admin only.
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    @QueryMapping
    public Page<AuditLogDto> auditLogForEntity(@Argument AuditEntityType entityType,
                                               @Argument Long entityId,
                                               @Argument int page,
                                               @Argument int size) {
        return auditLogService.getForEntity(entityType, entityId, PageRequest.of(page, size));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN', 'RECEPTIONIST')")
    @QueryMapping
    public Page<AuditLogDto> auditDashboardFeed(@Argument int page, @Argument int size) {
        return auditLogService.getDashboardFeed(PageRequest.of(page, size));
    }
}
