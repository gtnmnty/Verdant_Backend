package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.StylistBranchDto;
import com.verdant.salon_ecomm.models.entities.Branch;
import com.verdant.salon_ecomm.models.entities.Stylist;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StylistMapper {

    public AdminStylistsDto toAdminDto(Stylist stylist) {
        Branch branch = stylist.getBranch();
        // CreateBranchInput allows a branch without an address.
        StylistBranchDto branchDto = branch == null ? null :
            new StylistBranchDto(branch.getId().toString(), branch.getName(),
                branch.getAddress() != null ? branch.getAddress().toString() : null);

        return new AdminStylistsDto(
            stylist.getId().toString(),
            stylist.getName(),
            stylist.getEmail(),
            stylist.getPhone(),
            stylist.getAvatarUrl(),
            stylist.getBio(),
            branchDto,
            stylist.getServices(),
            stylist.getWorkingHours(),
            stylist.getStatus(),
            stylist.getCreatedAt(),
            stylist.getUpdatedAt()
        );
    }
}
