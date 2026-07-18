package com.verdant.salon_ecomm.entities;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHours {
    private String days;   // e.g. "Mon-Sat", "Tue-Sun", "Mon-Sun"
    private String open;   // e.g. "09:00"
    private String close;  // e.g. "20:00"
}