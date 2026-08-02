package com.verdant.salon_ecomm.dtos.account;

import java.util.List;

public record AccountPage(
    List<AccountDto> content,
    int totalElements,
    int totalPages,
    int page,
    int pageSize
) {}
