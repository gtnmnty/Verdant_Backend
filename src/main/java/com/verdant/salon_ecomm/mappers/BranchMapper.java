package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.branch.*;
import com.verdant.salon_ecomm.entities.Address;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.OperatingHours;
import org.springframework.stereotype.Component;

@Component
public class BranchMapper {

    public AdminBranchDto toAdminDto(Branch branch) {
        if (branch == null) { return null; }

        return new AdminBranchDto(
            branch.getId(),
            branch.getName(),
            toAddressDto(branch.getAddress()),
            branch.getPhone(),
            branch.getEmail(),
            toOperatingHours(branch.getOperatingHours()),
            branch.getGoogleMapsUrl(),
            branch.getImageUrl(),
            branch.getStatus(),
            branch.getCreatedAt(),
            branch.getUpdatedAt()
        );
    }

    public BranchAddressDto toAddressDto(Address address) {
        if (address == null) return null;
        return new BranchAddressDto(
            address.getLine1(),
            address.getLine2(),
            address.getCity(),
            address.getPostal(),
            address.getState(),
            address.getCountry()
        );
    }

    public Address toAddressEntity(BranchAddressInput input) {
        if (input == null) return null;
        return Address.builder()
            .line1(input.line1())
            .line2(input.line2())
            .city(input.city())
            .state(input.state())
            .postal(input.postal())
            .country(input.country() != null ? input.country() : "US")
            .build();
    }

    public OperatingHours toOperatingHours(OperatingHours operation){
        if (operation == null) { return null; }
        return new OperatingHours(
            operation.getDays(),
            operation.getOpen(),
            operation.getClose()
        );
    }

    public OperatingHours toOperatingHoursEntity(OperatingHoursInput input) {
        if (input == null) return null;
        return OperatingHours.builder()
            .days(input.getDays())
            .open(input.getOpen())
            .close(input.getClose())
            .build();
    }
}
