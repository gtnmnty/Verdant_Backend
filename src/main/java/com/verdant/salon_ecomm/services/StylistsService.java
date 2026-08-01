package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.BranchDto;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistAssignedToServicesEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistCreatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistDeletedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistImageUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistStatusChangedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistUpdatedEvent;
import com.verdant.salon_ecomm.dtos.stylists.events.StylistsBulkDeletedEvent;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.stylists.StylistSort;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.StylistsSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StylistsService {

    private final StylistRepository stylistRepository;
    private final BranchRepository branchRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AdminStylistsPage getStylists(
        StylistAccountStatus status, String branch, String search,
        List<UUID> services, StylistSort sort,
        int page, int pageSize
    ) {
        int normalizePage = Math.max(page - 1, 0) + 1;
        int normalizePageSize = Math.max(pageSize, 1);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<Stylist> result = stylistRepository.findAll(
            StylistsSpec.filter(status, branch, search, services),
            pageable
        );

        List<AdminStylistsDto> stylists = result.getContent()
            .stream().map(this::toAdminDto).toList();

        return new AdminStylistsPage(
            stylists, normalizePage, normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminStylistsDto getAdminStylistDetail(UUID id) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist Not Found"));

        return toAdminDto(stylist);
    }

    @Transactional
    public Stylist createStylist(CreateStylistInput input, UUID actorId) {
        Branch branch = branchRepository.findById(UUID.fromString(input.branchId()))
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        List<SalonService> services = input.serviceIds() == null || input.serviceIds().isEmpty()
            ? List.of()
            : salonServiceRepository.findAllById(
            input.serviceIds().stream().map(UUID::fromString).toList()
        );

        Stylist stylist = Stylist.builder()
            .name(input.name())
            .email(input.email())
            .phone(input.phone())
            .avatarUrl(input.avatarUrl())
            .bio(input.bio())
            .branch(branch)
            .services(services)
            .status(StylistAccountStatus.ACTIVE)
            .build();

        Stylist saved = stylistRepository.save(stylist);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new StylistCreatedEvent(saved, actor));

        return saved;
    }

    @Transactional
    public Stylist updateStylist(UUID id, UpdateStylistInput input, UUID actorId) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        String previousName = stylist.getName();
        String previousEmail = stylist.getEmail();
        String previousPhone = stylist.getPhone();
        String previousAvatarUrl = stylist.getAvatarUrl();
        String previousBio = stylist.getBio();
        Branch previousBranch = stylist.getBranch();

        if (input.name() != null) stylist.setName(input.name());
        if (input.email() != null) stylist.setEmail(input.email());
        if (input.phone() != null) stylist.setPhone(input.phone());
        if (input.avatarUrl() != null) stylist.setAvatarUrl(input.avatarUrl());
        if (input.bio() != null) stylist.setBio(input.bio());
        if (input.branchId() != null) {
            Branch branch = branchRepository.findById(UUID.fromString(input.branchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            stylist.setBranch(branch);
        }

        Stylist saved = stylistRepository.save(stylist);
        User actor = resolveActor(actorId);

        List<StylistUpdatedEvent.FieldChange> changes = new java.util.ArrayList<>();
        if (!Objects.equals(previousName, saved.getName())) {
            changes.add(new StylistUpdatedEvent.FieldChange("name", previousName, saved.getName()));
        }
        if (!Objects.equals(previousEmail, saved.getEmail())) {
            changes.add(new StylistUpdatedEvent.FieldChange("email", previousEmail, saved.getEmail()));
        }
        if (!Objects.equals(previousPhone, saved.getPhone())) {
            changes.add(new StylistUpdatedEvent.FieldChange("phone", previousPhone, saved.getPhone()));
        }
        if (!Objects.equals(previousBio, saved.getBio())) {
            changes.add(new StylistUpdatedEvent.FieldChange("bio", previousBio, saved.getBio()));
        }
        UUID prevBranchId = previousBranch != null ? previousBranch.getId() : null;
        UUID newBranchId = saved.getBranch() != null ? saved.getBranch().getId() : null;
        if (!Objects.equals(prevBranchId, newBranchId)) {
            changes.add(new StylistUpdatedEvent.FieldChange(
                "branch",
                previousBranch != null ? previousBranch.getName() : "none",
                saved.getBranch() != null ? saved.getBranch().getName() : "none"
            ));
        }

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new StylistUpdatedEvent(saved, actor, changes));
        }

        if (!Objects.equals(previousAvatarUrl, saved.getAvatarUrl())) {
            eventPublisher.publishEvent(new StylistImageUpdatedEvent(saved, actor));
        }

        return saved;
    }

    @Transactional
    public AdminStylistsDto deleteStylist(UUID id, UUID actorId) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        stylistRepository.deleteById(id);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new StylistDeletedEvent(stylist, actor));

        return toAdminDto(stylist);
    }

    @Transactional
    public Stylist updateStylistStatus(UUID id, StylistAccountStatus status, UUID actorId) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        StylistAccountStatus previousStatus = stylist.getStatus();
        stylist.setStatus(status);
        Stylist saved = stylistRepository.save(stylist);

        if (previousStatus != status) {
            User actor = resolveActor(actorId);
            eventPublisher.publishEvent(new StylistStatusChangedEvent(saved, actor, previousStatus));
        }

        return saved;
    }

    @Transactional
    public Stylist assignStylistToServices(UUID stylistId, List<UUID> serviceIds, UUID actorId) {
        Stylist stylist = stylistRepository.findById(stylistId)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        int previousServiceCount = stylist.getServices() != null ? stylist.getServices().size() : 0;

        List<SalonService> services = salonServiceRepository.findAllById(serviceIds);
        stylist.setServices(services);
        Stylist saved = stylistRepository.save(stylist);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(
            new StylistAssignedToServicesEvent(saved, actor, previousServiceCount, services.size())
        );

        return saved;
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    @Transactional
    public List<Stylist> deleteStylists(List<UUID> ids, UUID actorId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No stylist ids were provided.");
        }

        List<Stylist> stylists = stylistRepository.findAllById(ids);
        stylistRepository.deleteAll(stylists);

        if (!stylists.isEmpty()) {
            User actor = resolveActor(actorId);
            eventPublisher.publishEvent(new StylistsBulkDeletedEvent(stylists, actor));
        }

        return stylists;
    }

    private AdminStylistsDto toAdminDto(Stylist stylist) {
        Branch branch = stylist.getBranch();
        BranchDto branchDto = branch == null ? null :
            new BranchDto(branch.getId().toString(), branch.getName(), branch.getAddress().toString());

        return new AdminStylistsDto(
            stylist.getId().toString(),
            stylist.getName(),
            stylist.getEmail(),
            stylist.getPhone(),
            stylist.getAvatarUrl(),
            stylist.getBio(),
            branchDto,
            stylist.getServices(),
            stylist.getStatus(),
            stylist.getCreatedAt(),
            stylist.getUpdatedAt()
        );
    }

    private Sort toSort(StylistSort sort) {
        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case A_TO_Z -> Sort.by(Sort.Direction.ASC, "name");
            case Z_TO_A -> Sort.by(Sort.Direction.DESC, "name");
        };
    }
}