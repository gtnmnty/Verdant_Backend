package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.appointment.*;
import com.verdant.salon_ecomm.dtos.appointment.events.*;
import com.verdant.salon_ecomm.exceptions.AppointmentConflictException;
import com.verdant.salon_ecomm.exceptions.InvalidAppointmentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.AppointmentMapper;
import com.verdant.salon_ecomm.models.entities.*;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.appointments.*;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.specifications.AppointmentSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;

    private final UserRepository userRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final StylistRepository stylistRepository;
    private final BranchRepository branchRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- Queries ----------

    public AppointmentPage getMyAppointments(
        UUID userId, AppointmentClientFilter status, AppointmentTimeFrame timeframe,
        String search, AppointmentClientSort sort, int page, int pageSize
    ) {
        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.minusDays(30);
        OffsetDateTime windowEnd = now.plusDays(30);
        boolean archived = timeframe == AppointmentTimeFrame.ARCHIVED;

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toClientSort(sort));

        Specification<Appointment> spec = AppointmentSpec.filterMyAppointments(
            userId, status, search, windowStart, windowEnd, archived
        );

        Page<Appointment> result = appointmentRepository.findAll(spec, pageable);

        return new AppointmentPage(
            result.getContent(),
            normalizedPage,
            normalizedPageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public Appointment getAppointmentById(UUID id, UUID currentUserId, boolean isAdmin) {
        Appointment appointment = findAppointmentOrThrow(id);
        if (!isAdmin && !appointment.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Not your appointment");
        }
        return appointment;
    }

    public AdminAppointmentPage getAdminAppointments(
        AppointmentStatus status, UUID stylistId, String branch, AppointmentServiceType serviceType,
        String search, AdminAppointmentSort sort, int page, int pageSize
    ) {
        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toAdminSort(sort));

        Specification<Appointment> spec = AppointmentSpec.filterAdminAppointments(
            status, stylistId, branch, serviceType, search
        );

        Page<Appointment> result = appointmentRepository.findAll(spec, pageable);

        List<AdminAppointmentDto> items = result.getContent().stream()
            .map(appointmentMapper::toAdminDto)
            .toList();

        return new AdminAppointmentPage(
            items,
            normalizedPage,
            normalizedPageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminAppointmentDto getAdminAppointmentById(UUID id,  UUID currentUserId, boolean isAdmin) {
        return appointmentMapper.toAdminDto(getAppointmentById(id, currentUserId, isAdmin));
    }

    public AppointmentStatusCounts getAppointmentStatusCounts(UUID userId) {
        if (userId != null) {
            return new AppointmentStatusCounts(
                appointmentRepository.countByUser_Id(userId),
                appointmentRepository.countByUser_IdAndStatus(userId, AppointmentStatus.PENDING),
                (int) appointmentRepository.countByUser_IdAndStatus(userId, AppointmentStatus.UPCOMING),
                (int) appointmentRepository.countByUser_IdAndStatus(userId, AppointmentStatus.COMPLETED),
                (int) appointmentRepository.countByUser_IdAndStatus(userId, AppointmentStatus.CANCELLED)
            );
        }
        return new AppointmentStatusCounts(
            (int) appointmentRepository.count(),
            (int) appointmentRepository.countByStatus(AppointmentStatus.PENDING),
            (int) appointmentRepository.countByStatus(AppointmentStatus.UPCOMING),
            (int) appointmentRepository.countByStatus(AppointmentStatus.COMPLETED),
            (int) appointmentRepository.countByStatus(AppointmentStatus.CANCELLED)
        );
    }

    // Gets the details from the existing appointment.
    // Users will review the info and update if needed
    public AppointmentRebookDto getRebookDetails(UUID appointmentId, UUID userId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        if (!appointment.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this appointment");
        }

        return new AppointmentRebookDto(
            appointment.getService().getId(),
            appointment.getServiceName(),
            appointment.getServiceType(),
            appointment.getStylist() != null ? appointment.getStylist().getId() : null,
            appointment.getStylist() != null ? appointment.getStylist().getName() : null,
            appointment.getBranch() != null ? appointment.getBranch().getId() : null,
            appointment.getBranch() != null ? appointment.getBranch().getName() : null,
            appointment.getGuests()
        );
    }

    // ---------- Mutations ----------

    @Transactional
    // Staff may book for any active customer via input.userId(); everyone else always books for
    // themselves, whatever userId they send.
    public Appointment bookAppointment(CreateAppointmentInput input, UUID currentUserId, boolean isStaff) {
        User actor = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));

        User user = actor;
        if (isStaff && input.userId() != null && !input.userId().equals(currentUserId)) {
            user = userRepository.findById(input.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + input.userId()));
            if (!user.isAccountNonLocked() || !user.isEnabled()) {
                throw new InvalidAppointmentException("Appointments can only be booked for active, verified accounts");
            }
        }

        // M-20: no bookings in the past (also checked on reschedule and update).
        requireFutureTime(input.scheduledAt());

        // M-19: only ACTIVE services can be booked; an unpublished one answers "not found", like the catalog.
        SalonService service = findBookableServiceOrThrow(input.serviceId());

        Stylist stylist = input.stylistId() != null
            ? stylistRepository.findById(input.stylistId())
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found: " + input.stylistId()))
            : null;

        validateServiceLocation(input.serviceType(), input.branchId(), input.homeAddress());

        Branch branch = null;
        if (input.branchId() != null) {
            branch = branchRepository.findById(input.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + input.branchId()));
        }

        if (stylist != null) {
            OffsetDateTime start = input.scheduledAt();
            validateNoOverlap(stylist.getId(), start, service.getDurationMinutes(), null);
        }

        Appointment appointment = appointmentMapper.toEntity(input, user, service, stylist, branch);
        appointment.setAppointmentCode(generateAppointmentCode());

        Appointment saved = saveAppointmentSafely(appointment);
        eventPublisher.publishEvent(new AppointmentBookedEvent(saved, actor));
        return saved;
    }

    @Transactional
    public Appointment approveAppointment(UUID id, UUID actorId) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new InvalidAppointmentException(
                "Only PENDING appointments can be approved (current status: " + appointment.getStatus() + ")"
            );
        }

        appointment.setStatus(AppointmentStatus.UPCOMING);
        Appointment saved = appointmentRepository.save(appointment);

        eventPublisher.publishEvent(new AppointmentApprovedEvent(saved, resolveActor(actorId)));
        return saved;
    }

    @Transactional
    public Appointment rescheduleAppointment(
        UUID id, OffsetDateTime newScheduledAt, UUID currentUserId, boolean isAdmin
    ) {
        Appointment appointment = getAppointmentById(id, currentUserId, isAdmin);
        requireOwnerOrAdmin(appointment, currentUserId, isAdmin);
        requireNotTerminal(appointment);
        requireFutureTime(newScheduledAt); // M-20

        if (appointment.getStylist() != null) {
            validateNoOverlap(
                appointment.getStylist().getId(),
                newScheduledAt,
                appointment.getDurationMinutes(),
                appointment.getId()
            );
        }

        OffsetDateTime previousScheduledAt = appointment.getScheduledAt();
        appointment.setScheduledAt(newScheduledAt);
        // Staff approval covered the old time slot. A customer moving an approved appointment
        // sends it back for approval; staff reschedules keep the current status.
        if (!isAdmin && appointment.getStatus() == AppointmentStatus.UPCOMING) {
            appointment.setStatus(AppointmentStatus.PENDING);
        }
        Appointment saved = saveAppointmentSafely(appointment);

        User actor = resolveActor(currentUserId);
        eventPublisher.publishEvent(new AppointmentRescheduledEvent(saved, actor, previousScheduledAt));

        return saved;
    }

    @Transactional
    public Appointment cancelAppointment(UUID id, UUID currentUserId, boolean isAdmin) {
        Appointment appointment = getAppointmentById(id, currentUserId,  isAdmin);
        requireOwnerOrAdmin(appointment, currentUserId, isAdmin);
        requireNotTerminal(appointment);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);

        User actor = resolveActor(currentUserId);
        eventPublisher.publishEvent(new AppointmentCancelledEvent(saved, actor));

        return saved;
    }

    @Transactional
    public Appointment completeAppointment(UUID id, UUID currentUserId, boolean isAdmin) {
        Appointment appointment = getAppointmentById(id, currentUserId,  isAdmin);
        requireNotTerminal(appointment);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);

        User actor = resolveActor(currentUserId);
        eventPublisher.publishEvent(new AppointmentCompletedEvent(saved, actor));

        return saved;
    }

    @Transactional
    public List<Appointment> cancelAppointments(List<UUID> ids, UUID actorId) {
        User actor = resolveActor(actorId);
        boolean isStaffOrAdmin = isStaffOrAdmin(actor); // Use your existing helper or role check

        List<Appointment> eligible = appointmentRepository.findAllById(ids).stream()
            .filter(a -> a.getStatus() != AppointmentStatus.COMPLETED && a.getStatus() != AppointmentStatus.CANCELLED)
            .filter(a -> isStaffOrAdmin || (a.getUser() != null && a.getUser().getId().equals(actorId)))
            .toList();

        eligible.forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));
        List<Appointment> saved = appointmentRepository.saveAll(eligible);

        if (!saved.isEmpty()) {
            eventPublisher.publishEvent(new AppointmentsBulkCancelledEvent(saved, actor));
        }

        return saved;
    }

    @Transactional
    public Appointment updateAppointmentRequest(
        UUID id, UpdateAppointmentInput input,
        UUID currentUserId, boolean isAdmin
    ) {
        Appointment appointment = getAppointmentById(id, currentUserId, isAdmin);

        User previousUser = appointment.getUser();
        SalonService previousService = appointment.getService();
        Stylist previousStylist = appointment.getStylist();
        OffsetDateTime previousScheduledAt = appointment.getScheduledAt();
        Short previousGuests = appointment.getGuests();
        String previousNotes = appointment.getNotes();
        AppointmentServiceType previousServiceType = appointment.getServiceType();
        Branch previousBranch = appointment.getBranch();
        Map<String, Object> previousHomeAddress = appointment.getHomeAddress();

        if (input.userId() != null) {
            User user = userRepository.findById(input.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + input.userId()));
            appointment.setUser(user);
        }
        // M-19: switching to another service follows the booking rule (ACTIVE only). Re-saving the same
        // service is allowed, so an appointment whose service was unpublished later can still be edited.
        if (input.serviceId() != null && !input.serviceId().equals(previousService != null ? previousService.getId() : null)) {
            SalonService service = findBookableServiceOrThrow(input.serviceId());
            appointment.setService(service);
            appointment.setServiceName(service.getName());
            appointment.setPriceSnapshot(service.getPrice());
            appointment.setDurationMinutes(service.getDurationMinutes());
        }
        if (input.stylistId() != null) {
            Stylist stylist = stylistRepository.findById(input.stylistId())
                .orElseThrow(() -> new ResourceNotFoundException("Stylist not found: " + input.stylistId()));
            appointment.setStylist(stylist);
        }
        // M-20: a new time must be in the future. Only checked when the time actually changes, so notes or
        // guests of a past appointment can still be corrected.
        if (input.scheduledAt() != null
            && (previousScheduledAt == null || !input.scheduledAt().isEqual(previousScheduledAt))) {
            requireFutureTime(input.scheduledAt());
            appointment.setScheduledAt(input.scheduledAt());
        }
        if (input.guests() != null) {
            appointment.setGuests(input.guests().shortValue());
        }
        if (input.notes() != null) {
            appointment.setNotes(input.notes());
        }

        // ---- resolve final service-location state (patch fields override existing) ----
        AppointmentServiceType finalServiceType = input.serviceType() != null
            ? input.serviceType()
            : appointment.getServiceType();

        UUID finalBranchId = input.branchId() != null
            ? input.branchId()
            : (appointment.getBranch() != null ? appointment.getBranch().getId() : null);

        boolean hasHomeAddress = input.homeAddress() != null || appointment.getHomeAddress() != null;

        validateServiceLocation(finalServiceType, finalBranchId, hasHomeAddress);

        if (input.serviceType() != null) {
            appointment.setServiceType(finalServiceType);
        }
        if (input.branchId() != null) {
            Branch branch = branchRepository.findById(input.branchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found: " + input.branchId()));
            appointment.setBranch(branch);
        }

        if (input.homeAddress() != null) {
            appointment.setHomeAddress(appointmentMapper.toHomeAddressMap(input.homeAddress()));
        }

        if (appointment.getStylist() != null) {
            validateNoOverlap(
                appointment.getStylist().getId(),
                appointment.getScheduledAt(),
                appointment.getDurationMinutes(),
                appointment.getId()
            );
        }

        Appointment saved = saveAppointmentSafely(appointment);

        List<AppointmentUpdatedEvent.FieldChange> changes = buildAppointmentChanges(
            saved, previousUser, previousService, previousStylist, previousScheduledAt,
            previousGuests, previousNotes, previousServiceType, previousBranch, previousHomeAddress
        );

        if (!changes.isEmpty()) {
            User actor = resolveActor(currentUserId);
            eventPublisher.publishEvent(new AppointmentUpdatedEvent(saved, actor, changes));
        }

        return saved;
    }

    private List<AppointmentUpdatedEvent.FieldChange> buildAppointmentChanges(
        Appointment appointment, User previousUser, SalonService previousService, Stylist previousStylist,
        OffsetDateTime previousScheduledAt, Short previousGuests, String previousNotes,
        AppointmentServiceType previousServiceType, Branch previousBranch, Map<String, Object> previousHomeAddress
    ) {
        List<AppointmentUpdatedEvent.FieldChange> changes = new java.util.ArrayList<>();

        UUID prevUserId = previousUser != null ? previousUser.getId() : null;
        UUID newUserId = appointment.getUser() != null ? appointment.getUser().getId() : null;
        if (!Objects.equals(prevUserId, newUserId)) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "customer",
                previousUser != null ? previousUser.getFullName() : "none",
                appointment.getUser() != null ? appointment.getUser().getFullName() : "none"
            ));
        }

        UUID prevServiceId = previousService != null ? previousService.getId() : null;
        UUID newServiceId = appointment.getService() != null ? appointment.getService().getId() : null;
        if (!Objects.equals(prevServiceId, newServiceId)) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "service",
                previousService != null ? previousService.getName() : "none",
                appointment.getService() != null ? appointment.getService().getName() : "none"
            ));
        }

        UUID prevStylistId = previousStylist != null ? previousStylist.getId() : null;
        UUID newStylistId = appointment.getStylist() != null ? appointment.getStylist().getId() : null;
        if (!Objects.equals(prevStylistId, newStylistId)) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "stylist",
                previousStylist != null ? previousStylist.getName() : "unassigned",
                appointment.getStylist() != null ? appointment.getStylist().getName() : "unassigned"
            ));
        }

        if (!Objects.equals(previousScheduledAt, appointment.getScheduledAt())) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "scheduledAt",
                String.valueOf(previousScheduledAt),
                String.valueOf(appointment.getScheduledAt())
            ));
        }

        if (!Objects.equals(previousGuests, appointment.getGuests())) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "guests",
                String.valueOf(previousGuests),
                String.valueOf(appointment.getGuests())
            ));
        }

        if (!Objects.equals(previousNotes, appointment.getNotes())) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "notes",
                previousNotes != null ? previousNotes : "none",
                appointment.getNotes() != null ? appointment.getNotes() : "none"
            ));
        }

        if (!Objects.equals(previousServiceType, appointment.getServiceType())) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "serviceType",
                String.valueOf(previousServiceType),
                String.valueOf(appointment.getServiceType())
            ));
        }

        UUID prevBranchId = previousBranch != null ? previousBranch.getId() : null;
        UUID newBranchId = appointment.getBranch() != null ? appointment.getBranch().getId() : null;
        if (!Objects.equals(prevBranchId, newBranchId)) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "branch",
                previousBranch != null ? previousBranch.getName() : "none",
                appointment.getBranch() != null ? appointment.getBranch().getName() : "none"
            ));
        }

        if (!Objects.equals(previousHomeAddress, appointment.getHomeAddress())) {
            changes.add(new AppointmentUpdatedEvent.FieldChange(
                "homeAddress", "updated", "updated"
            ));
        }

        return changes;
    }

    @Transactional
    public Appointment deleteAppointment(UUID id, UUID actorId) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment cannot be found"));

        appointmentRepository.delete(appointment);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new AppointmentDeletedEvent(appointment, actor));

        return appointment;
    }

    @Transactional
    public List<Appointment> deleteAppointments(List<UUID> ids, UUID actorId) {
        List<Appointment> appointments = appointmentRepository.findAllById(ids);
        appointmentRepository.deleteAll(appointments);

        if (!appointments.isEmpty()) {
            User actor = resolveActor(actorId);
            eventPublisher.publishEvent(new AppointmentsBulkDeletedEvent(appointments, actor));
        }

        return appointments;
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    private Appointment findAppointmentOrThrow(UUID id) {
        return appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + id));
    }

    private void requireOwnerOrAdmin(Appointment appointment, UUID currentUserId, boolean isAdmin) {
        if (isAdmin) return;
        if (appointment.getUser() == null
            || !appointment.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to modify this appointment");
        }
    }

    // M-20: book, reschedule and update had no "is it in the future" check (and no @Future on the input),
    // so an appointment could be booked for last week. A null time is refused here too: booking without one
    // used to fail later with an unclear error. Applies to staff as well; past visits are not recorded here.
    private void requireFutureTime(OffsetDateTime scheduledAt) {
        // BusinessRuleException, not InvalidAppointmentException: that one's message is replaced by a generic
        // "Invalid appointment request.", and the user needs to know why the time was refused.
        if (scheduledAt == null) {
            throw BusinessRuleException.invalid("Choose a date and time for the appointment.");
        }
        if (!scheduledAt.isAfter(OffsetDateTime.now())) {
            throw BusinessRuleException.invalid("Appointments can only be booked for a future date and time.");
        }
    }

    // M-19: the catalog only lists ACTIVE services, but booking looked the service up with findById, so an
    // unpublished (INACTIVE) service could still be booked by id. Same message as an unknown id, so the
    // existence of hidden services isn't revealed.
    private SalonService findBookableServiceOrThrow(UUID serviceId) {
        return salonServiceRepository.findById(serviceId)
            .filter(service -> service.getStatus() == CollectionStatus.ACTIVE)
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    private void requireNotTerminal(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.CANCELLED) {
            throw BusinessRuleException.conflict("Cannot modify a " + status + " appointment");
        }
    }

    // Flushes so the overlap exclusion constraint fires here, inside the try, rather than at commit
    // where it would surface as a 500 instead of AppointmentConflictException.
    private Appointment saveAppointmentSafely(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new AppointmentConflictException("Stylist is already booked in that time slot");
            }
            throw e;
        }
    }

    // 23P01 = PostgreSQL exclusion_violation. Matching the SQLState doesn't depend on the constraint's name.
    private static boolean isExclusionViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private Sort toClientSort(AppointmentClientSort sort) {
        AppointmentClientSort effective = sort != null ? sort : AppointmentClientSort.DATE_SOONEST;
        return switch (effective) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case PRICE_LOW_TO_HIGH -> Sort.by(Sort.Direction.ASC, "priceSnapshot");
            case PRICE_HIGH_TO_LOW -> Sort.by(Sort.Direction.DESC, "priceSnapshot");
            case DATE_SOONEST -> Sort.by(Sort.Direction.ASC, "scheduledAt");
            case DATE_LATEST -> Sort.by(Sort.Direction.DESC, "scheduledAt");
        };
    }

    private Sort toAdminSort(AdminAppointmentSort sort) {
        AdminAppointmentSort effective = sort != null ? sort : AdminAppointmentSort.NEWEST;
        return switch (effective) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case CLIENT_NAME_ASC -> Sort.by(Sort.Direction.ASC, "user.firstName");
            case CLIENT_NAME_DESC -> Sort.by(Sort.Direction.DESC, "user.firstName");
            case SCHEDULED_SOONEST -> Sort.by(Sort.Direction.ASC, "scheduledAt");
            case SCHEDULED_LATEST -> Sort.by(Sort.Direction.DESC, "scheduledAt");
        };
    }

    private String generateAppointmentCode() {
        Long sequenceValue = appointmentRepository.getNextAppointmentCodeSequenceValue();
        return "AP-" + String.format("%06d", sequenceValue);
    }

    private void validateNoOverlap(UUID stylistId, OffsetDateTime start, Integer durationMinutes, UUID excludeId) {
        if (stylistId == null) return;
        if (appointmentRepository.existsOverlappingAppointment(stylistId, start, durationMinutes, excludeId)) {
            throw new AppointmentConflictException("Stylist is already booked in that time slot");
        }
    }

    private void validateServiceLocation(
        AppointmentServiceType serviceType, UUID branchId, AddressInput homeAddress
    ) {
        validateServiceLocation(serviceType, branchId, homeAddress != null);
    }

    private void validateServiceLocation(
        AppointmentServiceType serviceType, UUID branchId, boolean hasHomeAddress
    ) {
        if (serviceType == AppointmentServiceType.IN_SALON && branchId == null) {
            throw new InvalidAppointmentException("branchId is required for in-salon appointments");
        }
        if (serviceType == AppointmentServiceType.HOME_SERVICE && !hasHomeAddress) {
            throw new InvalidAppointmentException("homeAddress is required for home service appointments");
        }
    }

    private boolean isStaffOrAdmin(User actor) {
        if (actor == null || actor.getRole() == null) {
            return false;
        }

        return switch (actor.getRole()) {
            case ADMIN, MANAGER, RECEPTIONIST, OWNER -> true;
            default -> false;
        };
    }
}