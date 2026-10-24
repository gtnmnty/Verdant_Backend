package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.models.entities.SupportTicket;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketCategory;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketPriority;
import com.verdant.salon_ecomm.models.enums.support.SupportTicketStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

// Every method returns null when its filter is absent, so they compose with Specification.allOf(...).
public class SupportTicketSpec {

    public static Specification<SupportTicket> belongsTo(UUID userId) {
        return (root, query, cb) ->
            cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<SupportTicket> hasStatus(SupportTicketStatus status) {
        if (status == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    public static Specification<SupportTicket> hasPriority(SupportTicketPriority priority) {
        if (priority == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("priority"), priority);
    }

    public static Specification<SupportTicket> hasCategory(SupportTicketCategory category) {
        if (category == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("category"), category);
    }

    public static Specification<SupportTicket> assignedTo(UUID staffId) {
        if (staffId == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("assignedTo").get("id"), staffId);
    }

    public static Specification<SupportTicket> unassigned(Boolean unassignedOnly) {
        if (unassignedOnly == null || !unassignedOnly) return null;
        return (root, query, cb) ->
            cb.isNull(root.get("assignedTo"));
    }

    // URGENT first, then HIGH, NORMAL, LOW; newest first inside each group. Priority is stored as text, so a plain
    // "ORDER BY priority" would sort alphabetically (HIGH, LOW, NORMAL, URGENT). Use with an UNSORTED Pageable:
    // Spring Data only applies its own sort when one is given, so this ordering is left in place.
    public static Specification<SupportTicket> orderedByPriority() {
        return (root, query, cb) -> {
            // The same spec also builds the COUNT query, where an ORDER BY is invalid - skip it there.
            if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
                Expression<Integer> rank = cb.<Integer>selectCase()
                    .when(cb.equal(root.get("priority"), SupportTicketPriority.URGENT), 0)
                    .when(cb.equal(root.get("priority"), SupportTicketPriority.HIGH), 1)
                    .when(cb.equal(root.get("priority"), SupportTicketPriority.NORMAL), 2)
                    .otherwise(3);
                query.orderBy(cb.asc(rank), cb.desc(root.get("createdAt")));
            }
            return null;
        };
    }

    // Matches ticket number, subject, contact email, or the customer's name.
    public static Specification<SupportTicket> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = LikePatterns.contains(search);
        return (root, query, cb) -> cb.or(
            cb.like(cb.lower(root.get("ticketNumber")), pattern, LikePatterns.ESCAPE),
            cb.like(cb.lower(root.get("subject")), pattern, LikePatterns.ESCAPE),
            cb.like(cb.lower(root.get("contactEmail")), pattern, LikePatterns.ESCAPE),
            cb.like(cb.lower(root.join("user", JoinType.LEFT).get("fullName")), pattern, LikePatterns.ESCAPE)
        );
    }
}
