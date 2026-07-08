package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.BranchDto;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.*;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.StylistRepository;
import com.verdant.salon_ecomm.specifications.StylistsSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StylistsService {

    private final StylistRepository stylistRepository;
    private final BranchRepository branchRepository;
    private final SalonServiceRepository salonServiceRepository;

    public AdminStylistsPage getStylists(
        StylistAccountStatus status, String branch, String search,
        List<SalonService> services, StylistSort sort,
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

    public AdminStylistsDto getAdminStylistDetail(UUID id) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist Not Found"));

        return toAdminDto(stylist);
    }

    private Sort toSort(StylistSort sort) {
        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case A_TO_Z -> Sort.by(Sort.Direction.ASC, "name");
            case Z_TO_A -> Sort.by(Sort.Direction.DESC, "name");
        };
    }

    @Transactional
    public AdminStylistsDto createStylist(CreateStylistInput input) {
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

        return toAdminDto(stylistRepository.save(stylist));
    }

    @Transactional
    public AdminStylistsDto updateStylist(UpdateStylistInput input) {
        Stylist stylist = stylistRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

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

        return toAdminDto(stylistRepository.save(stylist));
    }

    @Transactional
    public AdminStylistsDto deleteStylist(UUID id) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        stylistRepository.deleteById(id);
        return toAdminDto(stylist);
    }

    @Transactional
    public AdminStylistsDto updateStylistStatus(UUID id, StylistAccountStatus status) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        stylist.setStatus(status);

        return toAdminDto(stylistRepository.save(stylist));
    }

    @Transactional
    public AdminStylistsDto assignStylistToServices(UUID stylistId, List<UUID> serviceIds) {
        Stylist stylist = stylistRepository.findById(stylistId)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        List<SalonService> services = salonServiceRepository.findAllById(serviceIds);
        stylist.setServices(services);

        return toAdminDto(stylistRepository.save(stylist));
    }

}