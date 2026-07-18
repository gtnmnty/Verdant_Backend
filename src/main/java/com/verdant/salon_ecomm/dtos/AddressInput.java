package com.verdant.salon_ecomm.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressInput(
    @NotBlank(message = "Address line 1 is required")
    @Size(max = 255)
    String line1,

    @Size(max = 255)
    String line2,

    @NotBlank(message = "City is required")
    @Size(max = 100)
    String city,

    @Size(max = 100)
    String state,

    @NotBlank(message = "Postal code is required")
    @Size(max = 20)
    String postal,

    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO code")
    String country
) {
    // Compact constructor: normalize default before validation-consuming code ever sees it
    public AddressInput {
        if (country == null || country.isBlank()) {
            country = "US";
        }
    }
}