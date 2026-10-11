package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.support.AddSupportTicketMessageInput;
import com.verdant.salon_ecomm.dtos.support.CreateSupportTicketInput;
import com.verdant.salon_ecomm.dtos.support.SupportTicketDetailDto;
import com.verdant.salon_ecomm.dtos.support.SupportTicketSummaryDto;
import com.verdant.salon_ecomm.dtos.support.admin.AdminAddSupportTicketMessageInput;
import com.verdant.salon_ecomm.dtos.support.admin.AdminSupportTicketPage;
import com.verdant.salon_ecomm.dtos.support.admin.AdminUpdateSupportTicketInput;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.support.AdminSupportTicketSort;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import com.verdant.salon_ecomm.services.SupportTicketService;   // NEXT STEP: not written yet
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class SupportTicketResolver {

    private final SupportTicketService supportTicketService;

    // ── Customer ─────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<SupportTicketSummaryDto> mySupportTickets(@Argument SupportTicketStatus status) {
        return supportTicketService.getMyTickets(getCurrentUserId(), status);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto mySupportTicket(@Argument UUID id) {
        return supportTicketService.getMyTicket(getCurrentUserId(), id);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto createSupportTicket(@Argument CreateSupportTicketInput input) {
        return supportTicketService.createTicket(getCurrentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto addSupportTicketMessage(@Argument AddSupportTicketMessageInput input) {
        return supportTicketService.addCustomerMessage(getCurrentUserId(), input);
    }

    // ── Staff (support desk) ─────────────────────────────────────

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public AdminSupportTicketPage adminSupportTickets(
        @Argument SupportTicketStatus status,
        @Argument SupportTicketPriority priority,
        @Argument SupportTicketCategory category,
        @Argument UUID assignedToId,
        @Argument Boolean unassignedOnly,
        @Argument String search,
        @Argument AdminSupportTicketSort sort,
        @Argument int page,
        @Argument int pageSize
    ) {
        return supportTicketService.getAdminTickets(
            status, priority, category, assignedToId, unassignedOnly, search, sort, page, pageSize);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminSupportTicket(@Argument UUID id) {
        return supportTicketService.getAdminTicket(id);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminAddSupportTicketMessage(@Argument AdminAddSupportTicketMessageInput input) {
        return supportTicketService.adminAddMessage(getCurrentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminUpdateSupportTicket(
        @Argument UUID id, @Argument AdminUpdateSupportTicketInput input
    ) {
        return supportTicketService.adminUpdateTicket(getCurrentUserId(), id, input);
    }

    // assigneeId = null unassigns the ticket.
    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminAssignSupportTicket(@Argument UUID id, @Argument UUID assigneeId) {
        return supportTicketService.adminAssignTicket(getCurrentUserId(), id, assigneeId);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("No authenticated user found");
    }
}
