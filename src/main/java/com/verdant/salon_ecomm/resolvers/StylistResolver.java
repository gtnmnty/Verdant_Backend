package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.StylistSort;
import com.verdant.salon_ecomm.services.StylistsService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StylistResolver {

    private final StylistsService stylistsService;

    @QueryMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminStylistsPage adminStylists(
        @Argument StylistAccountStatus status, @Argument String branchId, @Argument String search,
        @Argument List<SalonService> serviceIds, @Argument StylistSort sort,
        @Argument int page, @Argument int pageSize
    ) {
        return stylistsService.getStylists(status, branchId, search, serviceIds, sort, page, pageSize);
    }

    @QueryMapping("adminStylist")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminStylistsDto adminStylistsDetails(@Argument UUID id){
        return stylistsService.getAdminStylistDetail(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public Stylist createStylist(@Argument CreateStylistInput input) {
        return stylistsService.createStylist(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public Stylist updateStylist(@Argument UUID id, @Argument UpdateStylistInput input) {
        return stylistsService.updateStylist(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminStylistsDto deleteStylist(@Argument UUID id) {
        return stylistsService.deleteStylist(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public Stylist updateStylistStatus(@Argument UUID id, @Argument StylistAccountStatus status) {
        return stylistsService.updateStylistStatus(id, status);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public Stylist assignStylistToServices(@Argument UUID stylistId, @Argument List<UUID> serviceIds) {
        return stylistsService.assignStylistToServices(stylistId, serviceIds);
    }


}
