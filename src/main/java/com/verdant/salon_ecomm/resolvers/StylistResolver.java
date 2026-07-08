package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.StylistSort;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.services.StylistsService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class StylistResolver {

    private final BranchRepository branchRepository;
    private final StylistsService stylistsService;

    @QueryMapping
    public AdminStylistsPage adminStylists(
        @Argument StylistAccountStatus status, @Argument String branch, @Argument String search,
        @Argument List<SalonService> services, @Argument StylistSort sort,
        @Argument int page, @Argument int pageSize
    ) {
        return stylistsService.getStylists(status, branch, search, services, sort, page, pageSize);
    }

    @QueryMapping
    public AdminStylistsDto adminStylistsDetails(@Argument UUID id){
        return stylistsService.getAdminStylistDetail(id);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminStylistsDto createStylist(@Argument CreateStylistInput input){
        return stylistsService.createStylist(input);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminStylistsDto updateStylist(@Argument UpdateStylistInput input){
        return stylistsService.updateStylist(input);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminStylistsDto deleteStylist(@Argument UUID id){
        return stylistsService.deleteStylist(id);
    }


}
