package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsDto;
import com.verdant.salon_ecomm.dtos.stylists.AdminStylistsPage;
import com.verdant.salon_ecomm.dtos.stylists.CreateStylistInput;
import com.verdant.salon_ecomm.dtos.stylists.UpdateStylistInput;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.Stylist;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.models.enums.*;
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
        return new AdminStylistsDto(
            stylist.getId().toString(),
            stylist.getName(),
            stylist.getEmail(),
            stylist.getPhone(),
            stylist.getAvatarUrl(),
            stylist.getBio(),
            stylist.getBranch().toString(),
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
        Stylist stylist = Stylist.builder()
            .name(input.fullName())
            .email(input.email())
            .phone(input.phoneNumber())
            .avatarUrl(input.avatarUrl())
            .bio(input.bio())
            .status(StylistAccountStatus.ACTIVE)
            .build();

        return toAdminDto(stylistRepository.save(stylist));
    }

    @Transactional
    public AdminStylistsDto updateStylist(UpdateStylistInput input) {
        Stylist stylist = stylistRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        if (input.fullName() != null) stylist.setName(input.fullName());
        if (input.email() != null) stylist.setEmail(input.email());
        if (input.phoneNumber() != null) stylist.setPhone(input.phoneNumber());
        if (input.avatarUrl() != null) stylist.setAvatarUrl(input.avatarUrl());
        if (input.bio() != null) stylist.setBio(input.bio());
        if (input.offeredServices() != null) stylist.setServices(input.offeredServices());
        if (input.status() != null) stylist.setStatus(input.status());

        return toAdminDto(stylistRepository.save(stylist));
    }

    @Transactional
    public AdminStylistsDto deleteStylist(UUID id) {
        Stylist stylist = stylistRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Stylist not found"));

        stylistRepository.deleteById(id);
        return toAdminDto(stylist);
    }

}