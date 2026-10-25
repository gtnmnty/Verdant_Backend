package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.models.entities.SupportTicket;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository
    extends JpaRepository<SupportTicket, UUID>, JpaSpecificationExecutor<SupportTicket> {

    @Query(value = "SELECT nextval('support_ticket_number_seq')", nativeQuery = true)
    Long getNextTicketNumberSequenceValue();

    @EntityGraph(attributePaths = {"user", "assignedTo"})
    Optional<SupportTicket> findDetailedById(UUID id);

    // M-39: tickets are kept after an account is deleted (support history), but their contact-form snapshot
    // (name and email) is personal data, so it is overwritten with the anonymized values.
    @Modifying
    @Query("UPDATE SupportTicket t SET t.contactName = :name, t.contactEmail = :email WHERE t.user.id = :userId")
    int anonymizeContactForUser(@Param("userId") UUID userId, @Param("name") String name, @Param("email") String email);

    // Used to cap how many unresolved tickets one customer can have open at once.
    long countByUserIdAndStatusNot(UUID userId, SupportTicketStatus status);

    // Ownership check built into the query: a customer can never load someone else's ticket.
    @EntityGraph(attributePaths = {"user", "assignedTo"})
    Optional<SupportTicket> findDetailedByIdAndUserId(UUID id, UUID userId);

    // Re-declared with an entity graph so spec-based lists load user/assignee in the same query (no N+1).

    @Override
    @EntityGraph(attributePaths = {"user", "assignedTo"})
    @NonNull
    Page<SupportTicket> findAll(@Nullable Specification<SupportTicket> spec, @NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"user", "assignedTo"})
    @NonNull
    List<SupportTicket> findAll(@Nullable Specification<SupportTicket> spec, @NonNull Sort sort);
}
