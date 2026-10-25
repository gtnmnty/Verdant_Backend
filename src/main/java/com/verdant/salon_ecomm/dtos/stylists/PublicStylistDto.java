package com.verdant.salon_ecomm.dtos.stylists;

import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.entities.Stylist;

import java.util.UUID;

// Backs the GraphQL PublicStylist type (audit C-2). Built from the entity on purpose,
// so a field added to PublicStylist later can't silently expose email, phone or status:
// it would have to be added here first.
public record PublicStylistDto(
    UUID id,
    String name,
    String bio,
    String avatarUrl,
    Branch branch
) {
    public static PublicStylistDto from(Stylist stylist) {
        return new PublicStylistDto(
            stylist.getId(),
            stylist.getName(),
            stylist.getBio(),
            stylist.getAvatarUrl(),
            stylist.getBranch()
        );
    }
}
