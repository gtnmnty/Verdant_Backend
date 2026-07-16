package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.service.*;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ServiceSort;
import com.verdant.salon_ecomm.services.SalonServicesService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class SalonServiceResolver {

    private final SalonServicesService salonService;

    @QueryMapping
    public ServicePage services(
        @Argument String category, @Argument String search, @Argument ServiceSort sort,
        @Argument int page, @Argument int pageSize
    ){
        return salonService.getSalonServices(category, search, sort, page, pageSize);
    }

    @SchemaMapping(typeName = "SalonService", field = "category")
    public String category(SalonService service){
        return service.getItemCatalog().name();
    }

    @SchemaMapping(typeName = "SalonService", field = "durationInMinutes")
    public Integer durationInMinutes(SalonService service) {
        return service.getDurationMinutes();
    }

    @QueryMapping
    public SalonService service(@Argument UUID id){
        return salonService.getServiceDetail(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminServiceDto create(@Argument CreateServiceInput input){
        return salonService.createServiceInput(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminServiceDto update(@Argument UpdateServiceInput input){
        return salonService.updateServiceInput(input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminServiceDto deleteService(@Argument UUID id){
        return salonService.deleteService(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminServicePage adminServices(
        @Argument String category, @Argument String search,
        @Argument ServiceSort sort, @Argument CollectionStatus status,
        @Argument int page, @Argument int pageSize
    ){
        return salonService.getAdminServices(category, search, sort, status, page, pageSize);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'ADMIN')")
    public AdminServiceDto adminServiceDetail(@Argument UUID id){
        return salonService.getAdminServiceDto(id);
    }

}
