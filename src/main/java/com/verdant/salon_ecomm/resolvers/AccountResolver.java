package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.account.AccountDetailDto;
import com.verdant.salon_ecomm.dtos.account.AccountFilterInput;
import com.verdant.salon_ecomm.dtos.account.AccountPage;
import com.verdant.salon_ecomm.dtos.account.CreateAccountInput;
import com.verdant.salon_ecomm.dtos.account.AccountDto;
import com.verdant.salon_ecomm.dtos.account.UpdateAccountInput;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AccountResolver {

    private final AccountService accountService;

    // ---------- Queries ----------

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @QueryMapping
    public AccountPage accounts(
        @Argument AccountFilterInput filter,
        @Argument int page,
        @Argument int pageSize
    ) {
        return accountService.getAccounts(filter, page, pageSize);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @QueryMapping
    public AccountDetailDto account(@Argument UUID id) {
        return accountService.getAccountById(id);
    }

    // ---------- Mutations ----------

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @MutationMapping
    public AccountDetailDto createAccount(
        @Argument("input") CreateAccountInput input, @AuthenticationPrincipal User principal
    ) {
        return accountService.createAccount(input, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @MutationMapping
    public AccountDetailDto updateAccount(
        @Argument UUID id, @Argument("input") UpdateAccountInput input, @AuthenticationPrincipal User principal
    ) {
        return accountService.updateAccount(id, input, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @MutationMapping
    public boolean sendPasswordReset(@Argument UUID id, @AuthenticationPrincipal User principal) {
        return accountService.sendPasswordReset(id, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @MutationMapping
    public List<AccountDto> suspendAccounts(@Argument List<UUID> ids, @AuthenticationPrincipal User principal) {
        return accountService.suspendAccounts(ids, principal);
    }

    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','OWNER')")
    @MutationMapping
    public List<UUID> deleteAccounts(@Argument List<UUID> ids, @AuthenticationPrincipal User principal) {
        return accountService.deleteAccounts(ids, principal);
    }
}
