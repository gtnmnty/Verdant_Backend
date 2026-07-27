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

    private static final int DEFAULT_MAX_PAGE_SIZE = 100;
    private final AuditLogService auditLogService;

    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    @QueryMapping
    public Page<AuditLogDto> auditLogForEntity(
        @Argument AuditEntityType entityType,
        @Argument Long entityId,
        @Argument int page,
        @Argument int size
    ) {
        PageRequest pageable = createValidatedPageRequest(page, size);
        return auditLogService.getForEntity(entityType, entityId, pageable);
    }

    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN', 'RECEPTIONIST')")
    @QueryMapping
    public Page<AuditLogDto> auditDashboardFeed(@Argument int page, @Argument int size) {
        PageRequest pageable = createValidatedPageRequest(page, size);
        return auditLogService.getDashboardFeed(pageable);
    }

    private PageRequest createValidatedPageRequest(int page, int size) {
        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0) ? 10 : Math.min(size, DEFAULT_MAX_PAGE_SIZE);
        return PageRequest.of(validatedPage, validatedSize);
    }
}
