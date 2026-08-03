package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.account.*;
import com.verdant.salon_ecomm.dtos.account.events.AccountCreatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountPasswordResetRequestedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountUpdatedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsDeletedEvent;
import com.verdant.salon_ecomm.dtos.account.events.AccountsSuspendedEvent;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.exceptions.ForbiddenException;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.AccountMapper;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.repositories.AccountRepository;
import com.verdant.salon_ecomm.repositories.AppointmentRepository;
import com.verdant.salon_ecomm.repositories.OrderRepository;
import com.verdant.salon_ecomm.repositories.ReviewRepository;
import com.verdant.salon_ecomm.specifications.AccountSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordResetTokenService passwordResetTokenService;

    // Safety cap for bulk mutations — protects against an unbounded payload
    // locking rows / holding the transaction open too long.
    private static final int MAX_BULK_SIZE = 100;

    // Lower rank = more privileged. Used to stop an actor from creating or
    // promoting an account to a role senior to their own (privilege escalation).
    private static final Map<AccountRole, Integer> ROLE_RANK = Map.of(
        AccountRole.OWNER, 0,
        AccountRole.ADMIN, 1,
        AccountRole.MANAGER, 2,
        AccountRole.RECEPTIONIST, 3,
        AccountRole.STYLIST, 4,
        AccountRole.CUSTOMER, 5
    );

    // ---------- Queries ----------

    public AccountPage getAccounts(AccountFilterInput filter, int page, int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.clamp(pageSize, 1, 100);

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, Sort.by("fullName").ascending());

        String search = filter != null ? filter.search() : null;
        var role = filter != null ? filter.role() : null;
        var status = filter != null ? filter.status() : null;

        Specification<User> spec = AccountSpec.filterAccounts(status, role, search);

        Page<User> result = accountRepository.findAll(spec, pageable);

        return new AccountPage(
            result.getContent().stream().map(accountMapper::toResponse).toList(),
            (int) result.getTotalElements(),
            result.getTotalPages(),
            normalizedPage,
            normalizedPageSize
        );
    }

    public AccountDetailDto getAccountById(UUID id) {
        User user = findAccountOrThrow(id);
        return accountMapper.toDetailResponse(user);
    }

    // ---------- Mutations ----------

    @Transactional
    public AccountDetailDto createAccount(CreateAccountInput input, User actor) {
        // Case-insensitive uniqueness check — matches AccountSpec.hasEmail, which
        // lower-cases both sides. Without this, "Foo@x.com" and "foo@x.com" would
        // both be accepted as "unique".
        if (accountRepository.exists(AccountSpec.hasEmail(input.email()))) {
            throw new DataIntegrityViolationException("An account with this email already exists");
        }

        requireCanAssignRole(actor, input.role());

        String temporaryPassword = UUID.randomUUID().toString();
        User user = accountMapper.toEntity(input, passwordEncoder.encode(temporaryPassword));

        User saved = accountRepository.save(user);

        eventPublisher.publishEvent(new AccountCreatedEvent(saved, actor));

        String resetToken = passwordResetTokenService.issueToken(saved);
        eventPublisher.publishEvent(
            new AccountPasswordResetRequestedEvent(saved.getId(), saved.getEmail(), resetToken, actor)
        );

        return accountMapper.toDetailResponse(saved);
    }

    @Transactional
    public AccountDetailDto updateAccount(UUID id, UpdateAccountInput input, User actor) {
        User user = findAccountOrThrow(id);

        // Case-insensitive duplicate check when the email is actually changing.
        if (input.email() != null && !input.email().equalsIgnoreCase(user.getEmail())
            && accountRepository.exists(AccountSpec.hasEmail(input.email()))) {
            throw new DataIntegrityViolationException("An account with this email already exists");
        }

        if (input.role() != null && input.role() != user.getRole()) {
            requireCanAssignRole(actor, input.role());
        }

        boolean actorTargetsSelf = actor != null && actor.getId().equals(user.getId());
        if (actorTargetsSelf && input.status() != null && isLockedOutStatus(input.status())) {
            throw new ForbiddenException("You cannot lock yourself out by changing your own account status.");
        }
        if (actorTargetsSelf && input.role() != null && input.role() != user.getRole()) {
            throw new ForbiddenException("You cannot change your own account's role.");
        }

        String previousFullName = user.getFullName();
        String previousEmail = user.getEmail();
        String previousPhone = user.getPhone();
        AccountRole previousRole = user.getRole();
        AccountStatus previousStatus = user.getStatus();

        // A role/status change that moves the last active OWNER out of that seat
        // must be blocked explicitly — role-assignment checks above don't cover
        // "demoting/locking the only owner left".
        if (previousRole == AccountRole.OWNER
            && ((input.role() != null && input.role() != AccountRole.OWNER)
            || (input.status() != null && isLockedOutStatus(input.status())))) {
            requireAnotherActiveOwnerRemains();
        }

        accountMapper.updateEntity(user, input);
        User saved = accountRepository.save(user);

        List<AccountUpdatedEvent.FieldChange> changes = new ArrayList<>();
        if (!Objects.equals(previousFullName, saved.getFullName())) {
            changes.add(new AccountUpdatedEvent.FieldChange("fullName", previousFullName, saved.getFullName(), false));
        }
        if (!Objects.equals(previousEmail, saved.getEmail())) {
            changes.add(new AccountUpdatedEvent.FieldChange("email", previousEmail, saved.getEmail(), true));
        }
        if (!Objects.equals(previousPhone, saved.getPhone())) {
            changes.add(new AccountUpdatedEvent.FieldChange("phone", previousPhone, saved.getPhone(), false));
        }
        if (!Objects.equals(previousRole, saved.getRole())) {
            changes.add(new AccountUpdatedEvent.FieldChange(
                "role", String.valueOf(previousRole), String.valueOf(saved.getRole()), true
            ));
        }
        if (!Objects.equals(previousStatus, saved.getStatus())) {
            changes.add(new AccountUpdatedEvent.FieldChange(
                "status", String.valueOf(previousStatus), String.valueOf(saved.getStatus()), true
            ));
        }

        if (!changes.isEmpty()) {
            eventPublisher.publishEvent(new AccountUpdatedEvent(saved, actor, changes));
        }

        return accountMapper.toDetailResponse(saved);
    }

    @Transactional
    public boolean sendPasswordReset(UUID id, User actor) {
        User user = findAccountOrThrow(id);
        String resetToken = passwordResetTokenService.issueToken(user);
        eventPublisher.publishEvent(
            new AccountPasswordResetRequestedEvent(user.getId(), user.getEmail(), resetToken, actor)
        );
        return true;
    }

    @Transactional
    public List<AccountDto> suspendAccounts(List<UUID> ids, User actor) {
        Set<UUID> requestedIds = normalizeAndCapBulkIds(ids);

        List<User> accounts = accountRepository.findAllById(requestedIds);
        assertAllFound(requestedIds, accounts);

        if (actor != null && requestedIds.contains(actor.getId())) {
            throw new ForbiddenException("You cannot suspend your own account.");
        }

        Set<UUID> ownerIdsInBatch = accounts.stream()
            .filter(a -> a.getRole() == AccountRole.OWNER)
            .map(User::getId)
            .collect(Collectors.toSet());

        if (!ownerIdsInBatch.isEmpty()) {
            long remainingActiveOwners = accountRepository.countByRoleAndStatus(AccountRole.OWNER, AccountStatus.ACTIVE);
            long ownersAboutToLoseActiveStatus = accounts.stream()
                .filter(a -> ownerIdsInBatch.contains(a.getId()) && a.getStatus() == AccountStatus.ACTIVE)
                .count();
            if (remainingActiveOwners - ownersAboutToLoseActiveStatus <= 0) {
                throw new ForbiddenException("Cannot suspend the last active OWNER account.");
            }
        }

        accounts.forEach(account -> account.setStatus(AccountStatus.SUSPENDED));
        List<User> saved = accountRepository.saveAll(accounts);

        eventPublisher.publishEvent(new AccountsSuspendedEvent(saved, actor));

        return saved.stream().map(accountMapper::toResponse).toList();
    }

    @Transactional
    public List<UUID> deleteAccounts(List<UUID> ids, User actor) {
        Set<UUID> requestedIds = normalizeAndCapBulkIds(ids);

        List<User> accounts = accountRepository.findAllById(requestedIds);
        assertAllFound(requestedIds, accounts);

        if (actor != null && requestedIds.contains(actor.getId())) {
            throw new ForbiddenException("You cannot delete your own account.");
        }

        long remainingActiveOwners = accountRepository.countByRoleAndStatus(AccountRole.OWNER, AccountStatus.ACTIVE);
        long ownersInBatch = accounts.stream()
            .filter(a -> a.getRole() == AccountRole.OWNER && a.getStatus() == AccountStatus.ACTIVE)
            .count();
        if (ownersInBatch > 0 && remainingActiveOwners - ownersInBatch <= 0) {
            throw new ForbiddenException("Cannot delete the last active OWNER account.");
        }

        // User rows are referenced by Order/Appointment/Review (no ON DELETE CASCADE
        // configured on those FKs). Hard-deleting an account with that history would
        // either corrupt order/booking/review records or crash mid-batch with a raw
        // FK violation. Block up front with one IN-clause check per dependent table.
        List<UUID> idsWithOrders = orderRepository.findDistinctUserIdsWithOrders(requestedIds);
        List<UUID> idsWithAppointments = appointmentRepository.findDistinctUserIdsWithAppointments(requestedIds);
        List<UUID> idsWithReviews = reviewRepository.findDistinctUserIdsWithReviews(requestedIds);

        if (!idsWithOrders.isEmpty() || !idsWithAppointments.isEmpty() || !idsWithReviews.isEmpty()) {
            Set<UUID> blockedIds = new LinkedHashSet<>();
            blockedIds.addAll(idsWithOrders);
            blockedIds.addAll(idsWithAppointments);
            blockedIds.addAll(idsWithReviews);

            List<String> blockedNames = accounts.stream()
                .filter(a -> blockedIds.contains(a.getId()))
                .map(User::getFullName)
                .toList();

            throw new ForbiddenException(
                "Cannot delete the following accounts because they have order, appointment, "
                    + "or review history: " + String.join(", ", blockedNames)
                    + ". Suspend the account instead, or consider a soft-delete path."
            );
        }

        accountRepository.deleteAllInBatch(accounts);

        eventPublisher.publishEvent(new AccountsDeletedEvent(accounts, actor));

        return accounts.stream().map(User::getId).toList();
    }

    // ---------- Helpers ----------

    private User findAccountOrThrow(UUID id) {
        return accountRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private Set<UUID> normalizeAndCapBulkIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("No account ids were provided.");
        }
        // De-dupe first: without this, a request with repeated ids would fail the
        // "accounts.size() != ids.size()" check even when every id is valid, since
        // findAllById() naturally returns distinct rows.
        Set<UUID> requestedIds = new LinkedHashSet<>(ids);
        if (requestedIds.size() > MAX_BULK_SIZE) {
            throw new IllegalArgumentException(
                "Cannot act on more than " + MAX_BULK_SIZE + " accounts in a single request."
            );
        }
        return requestedIds;
    }

    private void assertAllFound(Set<UUID> requestedIds, List<User> found) {
        if (found.size() != requestedIds.size()) {
            Set<UUID> foundIds = found.stream().map(User::getId).collect(Collectors.toSet());
            Set<UUID> missing = new LinkedHashSet<>(requestedIds);
            missing.removeAll(foundIds);
            throw new ResourceNotFoundException("One or more accounts were not found: " + missing);
        }
    }

    private boolean isLockedOutStatus(AccountStatus status) {
        return status == AccountStatus.SUSPENDED
            || status == AccountStatus.BANNED
            || status == AccountStatus.INACTIVE;
    }

    // Blocks privilege escalation: an actor may only create/assign a role that is
    // no more senior than their own (e.g. a MANAGER can never grant ADMIN/OWNER).
    private void requireCanAssignRole(User actor, AccountRole targetRole) {
        if (actor == null || targetRole == null) return;

        Integer actorRank = ROLE_RANK.get(actor.getRole());
        Integer targetRank = ROLE_RANK.get(targetRole);
        if (actorRank == null || targetRank == null) return;

        if (actorRank > targetRank) {
            throw new ForbiddenException(
                "You do not have permission to assign the " + targetRole + " role."
            );
        }
    }

    private void requireAnotherActiveOwnerRemains() {
        long activeOwners = accountRepository.countByRoleAndStatus(AccountRole.OWNER, AccountStatus.ACTIVE);
        // The account being changed is still counted as an active OWNER in the DB
        // at this point (not yet saved), so needing "another" one means count > 1.
        if (activeOwners <= 1) {
            throw new ForbiddenException("Cannot remove the last active OWNER account.");
        }
    }
}