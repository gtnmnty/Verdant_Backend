package com.verdant.salon_ecomm.dtos.branch;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperatingHoursInput {
    @NotBlank
    private String days;

    @NotBlank
    private String open;

    @NotBlank
    private String close;
}
