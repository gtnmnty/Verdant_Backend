package com.verdant.salon_ecomm.models.enums.stylists;

public enum StylistAccountStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    ARCHIVED,
    // Offered by the admin stylist form but missing here, so saving it failed. Must stay in sync
    // with stylists.graphql (EnumSchemaParityTest). Not bookable: only ACTIVE stylists are public.
    PENDING
}
