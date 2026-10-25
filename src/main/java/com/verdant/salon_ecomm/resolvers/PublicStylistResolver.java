package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.stylists.PublicStylistDto;
import com.verdant.salon_ecomm.models.entities.Appointment;
import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

// Resolves every public path to a stylist (audit C-2). /graphql is permitAll, so these
// fields are reachable anonymously through services/service and by customers through
// their own appointments. They return PublicStylistDto, never the Stylist entity.
@Controller
public class PublicStylistResolver {

    // Only ACTIVE stylists are shown: suspended, inactive or pending staff must not be
    // advertised (or booked) from a public service page.
    // No extra query per service: SalonService.stylists is @BatchSize-loaded.
    @SchemaMapping(typeName = "SalonService", field = "stylists")
    public List<PublicStylistDto> stylists(SalonService service) {
        if (service.getStylists() == null) return List.of();
        return service.getStylists().stream()
            .filter(s -> s.getStatus() == StylistAccountStatus.ACTIVE)
            .map(PublicStylistDto::from)
            .toList();
    }

    // Not filtered by status: a customer's past booking should still name the stylist
    // who did it, even if that stylist has since left. Only public fields are returned.
    @SchemaMapping(typeName = "Appointment", field = "stylist")
    public PublicStylistDto stylist(Appointment appointment) {
        return appointment.getStylist() == null ? null : PublicStylistDto.from(appointment.getStylist());
    }
}
