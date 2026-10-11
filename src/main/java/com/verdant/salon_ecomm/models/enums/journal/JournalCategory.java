package com.verdant.salon_ecomm.models.enums.journal;

// GraphQL enum values can't contain spaces/ampersands,
// so the UI maps these to its labels (see journal.graphql).
public enum JournalCategory {
    COLOR,          // "Color"
    CUT_AND_STYLE,  // "Cut & Style"
    SPA_AND_SKIN,   // "Spa & Skin"
    BRIDAL,         // "Bridal"
    TEXTURE         // "Texture"
}
