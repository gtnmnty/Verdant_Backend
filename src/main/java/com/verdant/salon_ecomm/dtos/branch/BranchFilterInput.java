package com.verdant.salon_ecomm.dtos.branch;

import com.verdant.salon_ecomm.models.enums.BranchStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BranchFilterInput {
    private BranchStatus status;
    private String search;
}
