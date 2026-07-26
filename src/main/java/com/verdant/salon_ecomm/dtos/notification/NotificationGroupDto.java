package com.verdant.salon_ecomm.dtos.notification;

import java.time.LocalDate;
import java.util.List;

public record NotificationGroupDto(
    LocalDate date,
    List<NotificationResponseDto> notifications
) {}
