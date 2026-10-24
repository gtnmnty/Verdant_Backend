package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.support.*;
import com.verdant.salon_ecomm.dtos.support.admin.AdminAddSupportTicketMessageInput;
import com.verdant.salon_ecomm.dtos.support.admin.AdminSupportTicketPage;
import com.verdant.salon_ecomm.dtos.support.admin.AdminSupportTicketSummaryDto;
import com.verdant.salon_ecomm.dtos.support.admin.AdminUpdateSupportTicketInput;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.SupportTicketMapper;
import com.verdant.salon_ecomm.models.entities.SupportTicket;
import com.verdant.salon_ecomm.models.entities.SupportTicketMessage;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.support.AdminSupportTicketSort;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import com.verdant.salon_ecomm.repositories.SupportTicketMessageRepository;
import com.verdant.salon_ecomm.repositories.SupportTicketRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.SupportTicketSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import static com.verdant.salon_ecomm.utils.InputValidators.requireText;
import com.verdant.salon_ecomm.utils.InputValidators;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
public class SupportTicketService {

    private static final int MAX_SUBJECT = 200;
    private static final int MAX_MESSAGE = 5000;
    private static final int MAX_NAME = 100;
    private static final int MAX_EMAIL = 254;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_OPEN_TICKETS_PER_USER = 10;   // stops one account flooding the support desk
    private static final int MAX_MESSAGES_PER_TICKET = 200;    // stops one thread growing without limit

    // Same support-desk roles the resolver allows.
    private static final Set<AccountRole> STAFF_ROLES =
        EnumSet.of(AccountRole.ADMIN, AccountRole.MANAGER, AccountRole.OWNER, AccountRole.RECEPTIONIST);

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SupportTicketMapper mapper;

    // ── Customer ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SupportTicketSummaryDto> getMyTickets(UUID userId, SupportTicketStatus status) {
        Specification<SupportTicket> spec = Specification.allOf(
            SupportTicketSpec.belongsTo(userId),
            SupportTicketSpec.hasStatus(status)
        );
        List<SupportTicket> tickets = ticketRepository.findAll(spec, Sort.by(Sort.Order.desc("updatedAt")));

        // Customers never see internal staff notes - not even in the count.
        Map<UUID, Integer> counts = messageCounts(tickets, false);
        return tickets.stream()
            .map(t -> mapper.toSummary(t, counts.getOrDefault(t.getId(), 0)))
            .toList();
    }

    /** Null when the ticket doesn't exist OR belongs to someone else - the two cases look identical. */
    @Transactional(readOnly = true)
    public SupportTicketDetailDto getMyTicket(UUID userId, UUID ticketId) {
        return ticketRepository.findDetailedByIdAndUserId(ticketId, userId)
            .map(t -> mapper.toDetail(t, false))
            .orElse(null);
    }

    @Transactional
    public SupportTicketDetailDto createTicket(UUID userId, CreateSupportTicketInput input) {
        String subject = requireText(input.subject(), MAX_SUBJECT, "Subject");
        String message = requireText(input.message(), MAX_MESSAGE, "Message");
        String contactName = requireText(input.contactName(), MAX_NAME, "Name");
        String contactEmail = requireEmail(input.contactEmail());

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (ticketRepository.countByUserIdAndStatusNot(userId, SupportTicketStatus.RESOLVED) >= MAX_OPEN_TICKETS_PER_USER) {
            throw new IllegalStateException("You already have " + MAX_OPEN_TICKETS_PER_USER
                + " open tickets. Please wait for a reply or resolution before opening another.");
        }

        SupportTicket ticket = SupportTicket.builder()
            .ticketNumber("T-" + ticketRepository.getNextTicketNumberSequenceValue())
            .user(user)
            .subject(subject)
            .category(input.category() != null ? input.category() : SupportTicketCategory.OTHER)
            .contactName(contactName)
            .contactEmail(contactEmail)
            .build();
        ticket.addMessage(newMessage(user, false, false, message));

        // saveAndFlush so ids and createdAt exist before the DTO is built.
        return mapper.toDetail(ticketRepository.saveAndFlush(ticket), false);
    }

    // Customer reply. Replying to a RESOLVED ticket reopens it.
    @Transactional
    public SupportTicketDetailDto addCustomerMessage(UUID userId, AddSupportTicketMessageInput input) {
        String body = requireText(input.body(), MAX_MESSAGE, "Message");
        SupportTicket ticket = ticketRepository.findDetailedByIdAndUserId(input.ticketId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));  // same answer for "not yours"
        requireRoomForMessage(ticket);

        User author = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        ticket.addMessage(newMessage(author, false, false, body));
        if (ticket.getStatus() == SupportTicketStatus.RESOLVED) {
            ticket.setStatus(SupportTicketStatus.OPEN);
            ticket.setResolvedAt(null);
        }
        return mapper.toDetail(ticketRepository.saveAndFlush(ticket), false);
    }

    // ── Staff ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminSupportTicketPage getAdminTickets(
        SupportTicketStatus status, SupportTicketPriority priority, SupportTicketCategory category,
        UUID assignedToId, Boolean unassignedOnly, String search,
        AdminSupportTicketSort sort, int page, int pageSize
    ) {
        boolean byPriority = sort == AdminSupportTicketSort.PRIORITY_HIGH_TO_LOW;

        Specification<SupportTicket> spec = Specification.allOf(
            SupportTicketSpec.hasStatus(status),
            SupportTicketSpec.hasPriority(priority),
            SupportTicketSpec.hasCategory(category),
            SupportTicketSpec.assignedTo(assignedToId),
            SupportTicketSpec.unassigned(unassignedOnly),
            SupportTicketSpec.matchesSearch(search == null ? null : truncate(search.trim(), 100)),
            byPriority ? SupportTicketSpec.orderedByPriority() : null
        );

        // For priority ordering the Pageable must be unsorted (the spec supplies the ORDER BY).
        Sort order = switch (sort == null ? AdminSupportTicketSort.NEWEST : sort) {
            case OLDEST -> Sort.by(Sort.Order.asc("createdAt"));
            case RECENTLY_UPDATED -> Sort.by(Sort.Order.desc("updatedAt"));
            case PRIORITY_HIGH_TO_LOW -> Sort.unsorted();
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"));
        };
        Pageable pageable = PageRequests.of(page, pageSize, MAX_PAGE_SIZE, order);

        Page<SupportTicket> result = ticketRepository.findAll(spec, pageable);
        Map<UUID, Integer> counts = messageCounts(result.getContent(), true);

        List<AdminSupportTicketSummaryDto> items = result.getContent().stream()
            .map(t -> mapper.toAdminSummary(t, counts.getOrDefault(t.getId(), 0)))
            .toList();
        return new AdminSupportTicketPage(items, pageable.getPageNumber() + 1, pageable.getPageSize(),
            (int) result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public SupportTicketDetailDto getAdminTicket(UUID id) {
        return mapper.toDetail(findOrThrow(id), true);
    }


    // Staff reply or internal note. A public reply on an OPEN ticket moves it to IN_REVIEW and, if nobody owns the
    // ticket yet, assigns it to the replying staff member. Internal notes never change status or assignee.
    @Transactional
    public SupportTicketDetailDto adminAddMessage(UUID staffId, AdminAddSupportTicketMessageInput input) {
        String body = requireText(input.body(), MAX_MESSAGE, "Message");
        User staff = requireStaff(staffId);
        SupportTicket ticket = findOrThrow(input.ticketId());
        requireRoomForMessage(ticket);

        boolean internal = Boolean.TRUE.equals(input.internal());
        ticket.addMessage(newMessage(staff, true, internal, body));

        if (!internal) {
            if (ticket.getStatus() == SupportTicketStatus.OPEN) ticket.setStatus(SupportTicketStatus.IN_REVIEW);
            if (ticket.getAssignedTo() == null) ticket.setAssignedTo(staff);
        }
        return mapper.toDetail(ticketRepository.saveAndFlush(ticket), true);
    }

    // Both fields optional (null = unchanged). Moving to RESOLVED stamps resolvedAt; moving away clears it.
    @Transactional
    public SupportTicketDetailDto adminUpdateTicket(UUID staffId, UUID id, AdminUpdateSupportTicketInput input) {
        requireStaff(staffId);
        SupportTicket ticket = findOrThrow(id);

        if (input.priority() != null) ticket.setPriority(input.priority());

        SupportTicketStatus next = input.status();
        if (next != null && next != ticket.getStatus()) {
            ticket.setStatus(next);
            ticket.setResolvedAt(next == SupportTicketStatus.RESOLVED ? OffsetDateTime.now() : null);
        }
        return mapper.toDetail(ticketRepository.saveAndFlush(ticket), true);
    }

    // assigneeId = null unassigns. The assignee must be a support-desk staff member.
    @Transactional
    public SupportTicketDetailDto adminAssignTicket(UUID staffId, UUID id, UUID assigneeId) {
        requireStaff(staffId);
        SupportTicket ticket = findOrThrow(id);

        if (assigneeId == null) {
            ticket.setAssignedTo(null);
        } else {
            User assignee = userRepository.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            if (!STAFF_ROLES.contains(assignee.getRole())) {
                throw new IllegalArgumentException("Tickets can only be assigned to staff members");
            }
            ticket.setAssignedTo(assignee);
        }
        return mapper.toDetail(ticketRepository.saveAndFlush(ticket), true);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private SupportTicket findOrThrow(UUID id) {
        return ticketRepository.findDetailedById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + id));
    }

    // Re-checks the role in the service (the resolver already does), so the rule can't be bypassed from elsewhere.
    private User requireStaff(UUID staffId) {
        User user = userRepository.findById(staffId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!STAFF_ROLES.contains(user.getRole())) {
            throw new org.springframework.security.access.AccessDeniedException("Support staff only");
        }
        return user;
    }

    private void requireRoomForMessage(SupportTicket ticket) {
        if (ticket.getMessages().size() >= MAX_MESSAGES_PER_TICKET) {
            throw new IllegalStateException("This ticket has reached the message limit. Please open a new ticket.");
        }
    }

    private SupportTicketMessage newMessage(User author, boolean fromStaff, boolean internal, String body) {
        return SupportTicketMessage.builder()
            .author(author)
            .fromStaff(fromStaff)
            .internal(internal)
            .body(body)
            .build();
    }

    // ticketId -> message count, in ONE grouped query. includeInternal=false for anything a customer can see.
    private Map<UUID, Integer> messageCounts(List<SupportTicket> tickets, boolean includeInternal) {
        if (tickets.isEmpty()) return Map.of();
        List<UUID> ids = tickets.stream().map(SupportTicket::getId).toList();
        Map<UUID, Integer> counts = new HashMap<>();
        for (TicketMessageCount row : messageRepository.countByTicketIds(ids, includeInternal)) {
            counts.put(row.ticketId(), row.count().intValue());
        }
        return counts;
    }

    private String requireEmail(String value) {
        String email = requireText(value, MAX_EMAIL, "Email").toLowerCase(Locale.ROOT);
        if (!InputValidators.isEmail(email)) throw new IllegalArgumentException("Please enter a valid email address.");
        return email;
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
