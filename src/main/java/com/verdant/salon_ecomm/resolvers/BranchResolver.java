package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.branch.AdminBranchDto;
import com.verdant.salon_ecomm.dtos.branch.AdminBranchPage;
import com.verdant.salon_ecomm.dtos.branch.CreateBranchInput;
import com.verdant.salon_ecomm.dtos.branch.UpdateBranchInput;
import com.verdant.salon_ecomm.models.enums.BranchStatus;
import com.verdant.salon_ecomm.services.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class BranchResolver {

    private final BranchService branchService;

    // ---------- Queries ----------

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public AdminBranchPage adminBranches(
        @Argument BranchStatus status,
        @Argument String search,
        @Argument int page,
        @Argument int pageSize
    ) {
        return branchService.getAdminBranches(status, search, page, pageSize);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    @QueryMapping
    public AdminBranchDto adminBranch(@Argument UUID id) {
        return branchService.getAdminBranchById(id);
    }

    // ---------- Mutations ----------

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public AdminBranchDto createBranch(@Argument("input") CreateBranchInput input) {
        return branchService.createBranch(input);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public AdminBranchDto updateBranch(@Argument("input") UpdateBranchInput input) {
        return branchService.updateBranch(input);
    }

    @PreAuthorize("hasAnyRole('MANAGER','ADMIN','OWNER')")
    @MutationMapping
    public AdminBranchDto deleteBranch(@Argument UUID id) {
        return branchService.deleteBranch(id);
    }
}