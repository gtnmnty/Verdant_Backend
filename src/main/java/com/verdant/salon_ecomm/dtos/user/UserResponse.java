package com.verdant.salon_ecomm.dtos.user;

import java.util.UUID;

public record UserResponse(UUID id, String fullName, String email) {
}
