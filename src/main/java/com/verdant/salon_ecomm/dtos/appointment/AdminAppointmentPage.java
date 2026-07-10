package com.verdant.salon_ecomm.dtos.appointment;

import java.util.List;

public record AdminAppointmentPage(
    List<AdminAppointmentDto> items,
    int page,
    int pageSize,
    int totalElements,
    int totalPages
) {}
