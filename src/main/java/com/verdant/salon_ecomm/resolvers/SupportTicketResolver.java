package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.support.AddSupportTicketMessageInput;
import com.verdant.salon_ecomm.dtos.support.CreateSupportTicketInput;
import com.verdant.salon_ecomm.dtos.support.SupportTicketDetailDto;
import com.verdant.salon_ecomm.dtos.support.SupportTicketPage;
import com.verdant.salon_ecomm.dtos.support.admin.AdminAddSupportTicketMessageInput;
import com.verdant.salon_ecomm.dtos.support.admin.AdminSupportTicketPage;
import com.verdant.salon_ecomm.dtos.support.admin.AdminUpdateSupportTicketInput;
import com.verdant.salon_ecomm.models.enums.support.AdminSupportTicketSort;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Controller
@RequiredArgsConstructor
public class SupportTicketResolver {

    private final SupportTicketService supportTicketService;
    private final SecurityHelper securityHelper;

    // ── Customer ─────────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    // M-42: paginated (page is 1-based; defaults page 1, 10 per page, at most 50).
    public SupportTicketPage mySupportTickets(
        @Argument SupportTicketStatus status, @Argument int page, @Argument int pageSize
    ) {
        return supportTicketService.getMyTickets(securityHelper.currentUserId(), status, page, pageSize);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto mySupportTicket(@Argument UUID id) {
        return supportTicketService.getMyTicket(securityHelper.currentUserId(), id);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto createSupportTicket(@Argument CreateSupportTicketInput input) {
        return supportTicketService.createTicket(securityHelper.currentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public SupportTicketDetailDto addSupportTicketMessage(@Argument AddSupportTicketMessageInput input) {
        return supportTicketService.addCustomerMessage(securityHelper.currentUserId(), input);
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
        return supportTicketService.adminAddMessage(securityHelper.currentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminUpdateSupportTicket(
        @Argument UUID id, @Argument AdminUpdateSupportTicketInput input
    ) {
        return supportTicketService.adminUpdateTicket(securityHelper.currentUserId(), id, input);
    }

    // assigneeId = null unassigns the ticket.
    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER', 'RECEPTIONIST')")
    public SupportTicketDetailDto adminAssignSupportTicket(@Argument UUID id, @Argument UUID assigneeId) {
        return supportTicketService.adminAssignTicket(securityHelper.currentUserId(), id, assigneeId);
    }
}
