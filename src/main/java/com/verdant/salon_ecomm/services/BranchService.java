package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.branch.AdminBranchDto;
import com.verdant.salon_ecomm.dtos.branch.AdminBranchPage;
import com.verdant.salon_ecomm.dtos.branch.CreateBranchInput;
import com.verdant.salon_ecomm.dtos.branch.UpdateBranchInput;
import com.verdant.salon_ecomm.dtos.branch.events.BranchesBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.BranchMapper;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentStatus;
import com.verdant.salon_ecomm.repositories.AppointmentRepository;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.BranchSpecification;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final StylistRepository stylistRepository;
    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    // Safety cap: protects the DB/transaction from an unbounded payload
    // (accidental "select all" from the admin UI, scripted abuse, etc.).
    private static final int MAX_BULK_DELETE_SIZE = 100;

    // Appointments in these statuses are still "live" and must not silently
    // lose their branch — completed/canceled ones are historical and don't block.
    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES =
        List.of(AppointmentStatus.PENDING, AppointmentStatus.UPCOMING);

    public AdminBranchPage getAdminBranches(
        BranchStatus status, String search,
        int page, int pageSize
    ) {
        int normalizePage = Math.max(page - 1, 0) + 1;
        int normalizePageSize = Math.max(pageSize, 1);

        Pageable pageable = PageRequest.of(
            normalizePage - 1, normalizePageSize,
            Sort.by(Sort.Direction.ASC, "name")
        );

        Page<Branch> result = branchRepository.findAll(
            BranchSpecification.filterBy(status, search),
            pageable
        );

        List<AdminBranchDto> items = result.getContent().stream()
            .map(branchMapper::toAdminDto)
            .toList();

        return new AdminBranchPage(
            items,
            (int) result.getTotalElements(),
            normalizePage,
            normalizePageSize,
            result.getTotalPages()
        );
    }

    public AdminBranchDto getAdminBranchById(UUID id) {
        return branchMapper.toAdminDto(findBranchOrThrow(id));
    }

    @Transactional
    public AdminBranchDto createBranch(@Valid CreateBranchInput input) {
        Branch branch = Branch.builder()
            .name(input.name())
            .address(branchMapper.toAddressEntity(input.address()))
            .phone(input.phone())
            .email(input.email())
            .operatingHours(branchMapper.toOperatingHoursEntity(input.operatingHours()))
            .googleMapsUrl(input.googleMapsUrl())
            .imageUrl(input.imageUrl())
            .status(input.status() != null ? input.status() : BranchStatus.OPEN)
            .build();

        return branchMapper.toAdminDto(branchRepository.save(branch));
    }

    @Transactional
    public AdminBranchDto updateBranch(@Valid UpdateBranchInput input) {
        Branch branch = findBranchOrThrow(input.id());

        if (input.name() != null) branch.setName(input.name());
        if (input.address() != null) branch.setAddress(branchMapper.toAddressEntity(input.address()));
        if (input.phone() != null) branch.setPhone(input.phone());
        if (input.email() != null) branch.setEmail(input.email());
        if (input.operatingHours() != null) {
            branch.setOperatingHours(branchMapper.toOperatingHoursEntity(input.operatingHours()));
        }
        if (input.googleMapsUrl() != null) branch.setGoogleMapsUrl(input.googleMapsUrl());
        if (input.imageUrl() != null) branch.setImageUrl(input.imageUrl());
        if (input.status() != null) branch.setStatus(input.status());

        return branchMapper.toAdminDto(branchRepository.save(branch));
    }

    @Transactional
    public AdminBranchDto deleteBranch(UUID id) {
        Branch branch = findBranchOrThrow(id);
        AdminBranchDto dto = branchMapper.toAdminDto(branch);
        branchRepository.delete(branch);
        return dto;
    }

    @Transactional
    public List<AdminBranchDto> deleteBranches(List<UUID> ids, UUID actorId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No branch ids were provided.");
        }

        // De-dupe defensively — a client sending the same id twice shouldn't
        // affect counts, dependent checks, or the audit/notification summary.
        Set<UUID> requestedIds = new LinkedHashSet<>(ids);

        if (requestedIds.size() > MAX_BULK_DELETE_SIZE) {
            throw new IllegalArgumentException(
                "Cannot delete more than " + MAX_BULK_DELETE_SIZE + " branches in a single request."
            );
        }

        List<Branch> branches = branchRepository.findAllById(requestedIds);

        Set<UUID> foundIds = branches.stream().map(Branch::getId).collect(java.util.stream.Collectors.toSet());
        if (!foundIds.equals(requestedIds)) {
            Set<UUID> missing = new LinkedHashSet<>(requestedIds);
            missing.removeAll(foundIds);
            throw new ResourceNotFoundException("One or more branches were not found: " + missing);
        }

        List<UUID> branchIdsWithStylists = stylistRepository.findDistinctBranchIdsWithStylists(requestedIds);
        List<UUID> branchIdsWithActiveAppointments =
            appointmentRepository.findDistinctBranchIdsWithActiveAppointments(requestedIds, ACTIVE_APPOINTMENT_STATUSES);

        if (!branchIdsWithStylists.isEmpty() || !branchIdsWithActiveAppointments.isEmpty()) {
            Set<UUID> blockedIds = new LinkedHashSet<>();
            blockedIds.addAll(branchIdsWithStylists);
            blockedIds.addAll(branchIdsWithActiveAppointments);

            List<String> blockedNames = branches.stream()
                .filter(b -> blockedIds.contains(b.getId()))
                .map(Branch::getName)
                .toList();

            throw new IllegalStateException(
                "Cannot delete the following branches while they still have assigned stylists "
                    + "or active appointments: " + String.join(", ", blockedNames)
                    + ". Reassign stylists and resolve pending/upcoming appointments first."
            );
        }

        // Single batched statement rather than N individual deletes.
        branchRepository.deleteAllInBatch(branches);

        User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
        eventPublisher.publishEvent(new BranchesBulkDeletedEvent(branches, actor));

        return branches.stream().map(branchMapper::toAdminDto).toList();
    }

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));
    }
}