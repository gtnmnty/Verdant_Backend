package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.AddressInput;
import com.verdant.salon_ecomm.dtos.appointment.*;
import com.verdant.salon_ecomm.entities.*;
import com.verdant.salon_ecomm.exceptions.AppointmentConflictException;
import com.verdant.salon_ecomm.exceptions.InvalidAppointmentException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.AppointmentMapper;
import com.verdant.salon_ecomm.models.enums.appointments.*;
import com.verdant.salon_ecomm.repositories.*;
import com.verdant.salon_ecomm.specifications.AppointmentSpec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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

        return saveAppointmentSafely(appointment);
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

        appointment.setScheduledAt(newScheduledAt);
        return saveAppointmentSafely(appointment);
    }

    @Transactional
    public Appointment cancelAppointment(UUID id, UUID currentUserId, boolean isAdmin) {
        Appointment appointment = getAppointmentById(id, currentUserId,  isAdmin);
        requireOwnerOrAdmin(appointment, currentUserId, isAdmin);
        requireNotTerminal(appointment);
        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment completeAppointment(UUID id, UUID currentUserId, boolean isAdmin) {
        Appointment appointment = getAppointmentById(id, currentUserId,  isAdmin);
        requireNotTerminal(appointment);
        appointment.setStatus(AppointmentStatus.COMPLETED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public List<Appointment> cancelAppointments(List<UUID> ids) {
        List<Appointment> eligible = appointmentRepository.findAllById(ids).stream()
            .filter(a -> a.getStatus() != AppointmentStatus.COMPLETED && a.getStatus() != AppointmentStatus.CANCELLED)
            .toList();
        eligible.forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));
        return appointmentRepository.saveAll(eligible);
    }

    @Transactional
    public Appointment updateAppointmentRequest(
        UUID id, UpdateAppointmentInput input,
        UUID currentUserId, boolean isAdmin
    ) {
        Appointment appointment = getAppointmentById(id, currentUserId, isAdmin);

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

        return saveAppointmentSafely(appointment);
    }

    @Transactional
    public Appointment deleteAppointment(UUID id) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Appointment cannot be found"));

        appointmentRepository.delete(appointment);
        return appointment;
    }

    @Transactional
    public List<Appointment> deleteAppointments(List<UUID> ids) {
        List<Appointment> appointments = appointmentRepository.findAllById(ids);
        appointmentRepository.deleteAll(appointments);
        return appointments;
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
}