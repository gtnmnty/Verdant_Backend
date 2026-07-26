package com.verdant.salon_ecomm.dtos.notification;

import java.util.List;

public record NotificationPageDto(
    List<NotificationResponseDto> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    long unreadCount
) {}
