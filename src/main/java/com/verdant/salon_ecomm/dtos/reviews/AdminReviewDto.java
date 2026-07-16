package com.verdant.salon_ecomm.dtos.reviews;

import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminReviewDto(
    UUID id,
    ReviewUserDto user,
    ItemType itemType,
    UUID targetId,
    String itemName,
    AppointmentServiceType serviceType,
    String serviceName,
    short stars,
    String text,
    OffsetDateTime createdAt
) {}
