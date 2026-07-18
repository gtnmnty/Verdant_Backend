package com.verdant.salon_ecomm.dtos.branch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHoursInput {
    private String days;
    private String open;
    private String close;
}
