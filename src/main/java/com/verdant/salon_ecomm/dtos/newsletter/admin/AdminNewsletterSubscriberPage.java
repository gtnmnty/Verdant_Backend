package com.verdant.salon_ecomm.dtos.newsletter.admin;

import java.util.List;

public record AdminNewsletterSubscriberPage(
    List<AdminNewsletterSubscriberDto> items,
    int page,
    int pageSize,
    int totalItems,
    int totalPages
) {}
