package com.verdant.salon_ecomm.services;

import com.cloudinary.provisioning.Account;
import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.appointment.*;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentBookedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentCompletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentDeletedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentRescheduledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentUpdatedEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkCancelledEvent;
import com.verdant.salon_ecomm.dtos.appointment.events.AppointmentsBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.*;
import com.verdant.salon_ecomm.exceptions.AppointmentConflictException;
import com.verdant.salon_ecomm.exceptions.InvalidAppointmentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.AppointmentMapper;
import com.verdant.salon_ecomm.models.enums.AccountRole;
import com.verdant.salon_ecomm.models.enums.appointments.*;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.specifications.AppointmentSpec;
import jakarta.persistence.EntityNotFoundException;
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
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.clamp(pageSize, 1, 100);

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
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.clamp(pageSize, 1, 100);

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

    // ---------- Mutations ----------

    @Transactional
    public Appointment bookAppointment(CreateAppointmentInput input, UUID currentUserId) {
        User user = userRepository.findById(currentUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + currentUserId));

        SalonService service = salonServiceRepository.findById(input.serviceId())
            .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + input.serviceId()));

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
        eventPublisher.publishEvent(new AppointmentBookedEvent(saved));
        return saved;
    }

    @Transactional
    public Appointment rescheduleAppointment(
        UUID id, OffsetDateTime newScheduledAt, UUID currentUserId, boolean isAdmin
    ) {
        Appointment appointment = getAppointmentById(id, currentUserId, isAdmin);
        requireOwnerOrAdmin(appointment, currentUserId, isAdmin);
        requireNotTerminal(appointment);

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
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + input.userId()));
            appointment.setUser(user);
        }
        if (input.serviceId() != null) {
            SalonService service = salonServiceRepository.findById(input.serviceId())
                .orElseThrow(() -> new EntityNotFoundException("Service not found: " + input.serviceId()));
            appointment.setService(service);
            appointment.setServiceName(service.getName());
            appointment.setPriceSnapshot(service.getPrice());
            appointment.setDurationMinutes(service.getDurationMinutes());
        }
        if (input.stylistId() != null) {
            Stylist stylist = stylistRepository.findById(input.stylistId())
                .orElseThrow(() -> new EntityNotFoundException("Stylist not found: " + input.stylistId()));
            appointment.setStylist(stylist);
        }
        if (input.scheduledAt() != null) {
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

    private void requireNotTerminal(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.CANCELLED) {
            throw new IllegalStateException("Cannot modify a " + status + " appointment");
        }
    }

    private Appointment saveAppointmentSafely(Appointment appointment) {
        try {
            return appointmentRepository.save(appointment);
        } catch (DataIntegrityViolationException e) {
            String message = e.getMessage();
            if (message != null && message.contains("overlapping_appointment_constraint")) {
                throw new AppointmentConflictException("Stylist is already booked in that time slot");
            }
            throw e;
        }
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

    private void validateNoOverlap(UUID stylistId, OffsetDateTime start, Integer end, UUID excludeId) {
        if (stylistId == null) return;
        if (appointmentRepository.existsOverlappingAppointment(stylistId, start, end, excludeId)) {
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