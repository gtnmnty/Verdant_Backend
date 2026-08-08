package com.verdant.salon_ecomm.dtos.audit;

import java.util.List;

public record AuditLogPage(
    List<AuditLogDto> content,
    int totalElements,
    boolean hasNext
) {}
