package com.verdant.salon_ecomm.dtos.reviews;

import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;

public record ReviewTargetInfo(
    String itemName,
    AppointmentServiceType serviceType,
    String serviceName
) {}
