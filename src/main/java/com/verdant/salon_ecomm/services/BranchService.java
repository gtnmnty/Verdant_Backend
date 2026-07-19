package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.branch.AdminBranchDto;
import com.verdant.salon_ecomm.dtos.branch.AdminBranchPage;
import com.verdant.salon_ecomm.dtos.branch.CreateBranchInput;
import com.verdant.salon_ecomm.dtos.branch.UpdateBranchInput;
import com.verdant.salon_ecomm.entities.Branch;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.BranchMapper;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import com.verdant.salon_ecomm.repositories.BranchRepository;
import com.verdant.salon_ecomm.specifications.BranchSpecification;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Validated
public class BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;

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
            normalizePage,
            normalizePageSize,
            (int) result.getTotalElements(),
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
        if (input.operatingHours() != null) branch.setOperatingHours(branchMapper.toOperatingHoursEntity(input.operatingHours()));
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

    private Branch findBranchOrThrow(UUID id) {
        return branchRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Branch not found with id: " + id));
    }
}