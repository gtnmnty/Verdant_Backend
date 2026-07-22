package com.verdant.salon_ecomm.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Address {

    @Column(name = "address_line1", length = 255)
    private String line1;

    @Column(name = "address_line2", length = 255)
    private String line2;

    @Column(name = "address_city", length = 100)
    private String city;

    @Column(name = "address_state", length = 100)
    private String state;

    @Column(name = "address_postal", length = 20)
    private String postal;

    @Column(name = "address_country", length = 5)
    @Builder.Default
    private String country = "US";

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (line1 != null && !line1.trim().isEmpty()) sb.append(line1.trim());
        if (line2 != null && !line2.trim().isEmpty()) sb.append(", ").append(line2.trim());
        if (city != null && !city.trim().isEmpty()) sb.append(", ").append(city.trim());
        if (state != null && !state.trim().isEmpty()) sb.append(", ").append(state.trim());
        if (postal != null && !postal.trim().isEmpty()) sb.append(" ").append(postal.trim());
        if (country != null && !country.trim().isEmpty()) sb.append(", ").append(country.trim());

        return sb.toString().replaceAll("^,\\s*", ""); // Cleans up leading commas if line1 is empty
    }
}
