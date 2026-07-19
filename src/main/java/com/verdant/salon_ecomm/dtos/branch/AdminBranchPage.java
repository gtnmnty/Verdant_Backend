package com.verdant.salon_ecomm.dtos.branch;

import java.util.List;

public record AdminBranchPage(
    List<AdminBranchDto> items,
    int totalItems,
    int page,
    int pageSize,
    int totalPages
) {}
