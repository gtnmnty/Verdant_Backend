package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.branch.OperatingHoursInput;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.events.*;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.StylistMapper;
import com.verdant.salon_ecomm.models.entities.*;
import com.verdant.salon_ecomm.models.enums.stylists.StylistAccountStatus;
import com.verdant.salon_ecomm.models.enums.stylists.StylistSort;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.StylistsSpec;
import com.verdant.salon_ecomm.utils.PageRequests;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StylistsService {

    private final StylistMapper stylistMapper;
    private final StylistRepository stylistRepository;
    private final BranchRepository branchRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    public AdminStylistsPage getStylists(
        StylistAccountStatus status, UUID branchId, String search,
        List<UUID> services, StylistSort sort,
        int page, int pageSize
    ) {
        int normalizePage = PageRequests.page(page);
        int normalizePageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizePage - 1, normalizePageSize, toSort(sort));

        Page<Stylist> result = stylistRepository.findAll(
            StylistsSpec.filter(status, branchId, search, services),
            pageable
        );

        List<AdminStylistsDto> stylists = result.getContent()
            .stream().map(stylistMapper::toAdminDto).toList();

        return new AdminStylistsPage(
            stylists, normalizePage, normalizePageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminStylistsDto getAdminStylistDetail(UUID id) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist Not Found"));

        return stylistMapper.toAdminDto(stylist);
    }

    // ---------- Mutations ----------

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
            .services(new ArrayList<>())
            .workingHours(Optional.ofNullable(toWorkingHours(input.workingHours())).orElseGet(ArrayList::new))
            .status(StylistAccountStatus.ACTIVE)
            .build();

        Stylist saved = stylistRepository.save(stylist);
        // Link through the owning side (SalonService.stylists) so the join rows are written
        // after save(), so the stylist is managed and has an id for the join table.
        replaceServiceAssignments(saved, services);

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
        String previousHours = formatHours(stylist.getWorkingHours());

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

        List<OperatingHours> newHours = toWorkingHours(input.workingHours());
        if (newHours != null) stylist.setWorkingHours(newHours);

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

        String currentHours = formatHours(saved.getWorkingHours());
        if (!Objects.equals(previousHours, currentHours)) {
            changes.add(new StylistUpdatedEvent.FieldChange("workingHours", previousHours, currentHours));
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
        // H-13: was stylist.setServices(services), which only touches the inverse side and saves nothing.
        replaceServiceAssignments(stylist, services);
        Stylist saved = stylistRepository.save(stylist);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(
            new StylistAssignedToServicesEvent(saved, actor, previousServiceCount, services.size())
        );

        return saved;
    }

    @Transactional
    public AdminStylistsDto deleteStylist(UUID id, UUID actorId) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        // initialize the lazy collection before the row is deleted
        Hibernate.initialize(stylist.getServices());

        stylistRepository.deleteById(id);

        User actor = resolveActor(actorId);
        eventPublisher.publishEvent(new StylistDeletedEvent(stylist, actor));

        return stylistMapper.toAdminDto(stylist);
    }

    @Transactional
    public List<AdminStylistsDto> deleteStylists(List<UUID> ids, UUID actorId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No stylist ids were provided.");
        }

        List<Stylist> stylists = stylistRepository.findAllById(ids);
        stylists.forEach(s -> Hibernate.initialize(s.getServices()));   // load BEFORE deleting

        stylistRepository.deleteAllByIdInBatch(ids);

        if (!stylists.isEmpty()) {
            User actor = resolveActor(actorId);
            eventPublisher.publishEvent(new StylistsBulkDeletedEvent(stylists, actor));
        }

        return stylists.stream().map(stylistMapper::toAdminDto).toList();
    }

    // ---------- Helpers ----------

    // H-13: the stylist_services join table is owned by SalonService.stylists (Stylist.services is
    // mappedBy = "stylists"). Hibernate only writes join rows from the owning side, so assigning has to
    // go through each SalonService. Flow:
    //   1. Services the stylist no longer belongs to: remove the stylist from service.stylists.
    //   2. Newly picked services: add the stylist to service.stylists (skips ones already linked).
    //   3. Mirror the result on stylist.services, so the returned entity and the event are correct
    //      without reloading.
    // The services are managed entities, so dirty checking writes the changes on commit; no save() needed.
    // Matching is by identity: inside one transaction Hibernate returns one instance per row, and
    // neither entity overrides equals().
    // Tradeoffs:
    //   - SalonService.stylists is a List (a Hibernate "bag"). Removing one element makes Hibernate
    //     delete all of that service's join rows and re-insert the rest. Fine at salon scale; changing
    //     it to a Set would make it a single-row DELETE but changes the entity's API.
    //   - Making Stylist the owning side instead would mean moving @JoinTable and checking every
    //     service-side write, which is a bigger change than fixing the writes here.
    //   - Unknown service ids are silently dropped by findAllById, as before.
    private void replaceServiceAssignments(Stylist stylist, List<SalonService> newServices) {
        List<SalonService> current = stylist.getServices() != null ? stylist.getServices() : List.of();

        for (SalonService service : current) {
            if (!newServices.contains(service) && service.getStylists() != null) {
                service.getStylists().remove(stylist);
            }
        }

        for (SalonService service : newServices) {
            if (service.getStylists() == null) {
                service.setStylists(new ArrayList<>());
            }
            if (!service.getStylists().contains(stylist)) {
                service.getStylists().add(stylist);
            }
        }

        stylist.setServices(new ArrayList<>(newServices));
    }

    private User resolveActor(UUID actorId) {
        return actorId != null ? userRepository.findById(actorId).orElse(null) : null;
    }

    private Sort toSort(StylistSort sort) {
        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case A_TO_Z -> Sort.by(Sort.Direction.ASC, "name");
            case Z_TO_A -> Sort.by(Sort.Direction.DESC, "name");
        };
    }

    // null in = "not provided" (null out); empty list = "clear".
    private List<OperatingHours> toWorkingHours(List<OperatingHoursInput> inputs) {
        if (inputs == null) return null;
        List<OperatingHours> result = new ArrayList<>();
        for (OperatingHoursInput in : inputs) {
            String days = in.getDays() == null ? "" : in.getDays().trim();
            String open = in.getOpen() == null ? "" : in.getOpen().trim();
            String close = in.getClose() == null ? "" : in.getClose().trim();

            if (days.isEmpty()) {
                throw new IllegalArgumentException("Working hours need a days value, e.g. \"Mon-Fri\"");
            }
            if (!HH_MM.matcher(open).matches() || !HH_MM.matcher(close).matches()) {
                throw new IllegalArgumentException("Working hours must use 24-hour HH:mm format");
            }
            if (open.compareTo(close) >= 0) { // zero-padded HH:mm compares correctly as text
                throw new IllegalArgumentException("Working hours: opening time must be before closing time");
            }
            result.add(new OperatingHours(days, open, close));
        }
        return result;
    }

    private String formatHours(List<OperatingHours> hours) {
        if (hours == null || hours.isEmpty()) return "none";
        return hours.stream()
            .map(h -> h.getDays() + " " + h.getOpen() + "-" + h.getClose())
            .collect(Collectors.joining("; "));
    }
}