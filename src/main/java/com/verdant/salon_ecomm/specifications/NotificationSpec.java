package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.dtos.notification.NotificationQueryDto;
import com.verdant.salon_ecomm.entities.Notification;
import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NotificationSpec {

    public static Specification<Notification> forUserWithFilters(UUID userId, NotificationQueryDto query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (query.readFilter() == NotificationReadFilter.READ) {
                predicates.add(cb.isTrue(root.get("isRead")));
            } else if (query.readFilter() == NotificationReadFilter.UNREAD) {
                predicates.add(cb.isFalse(root.get("isRead")));
            }

            if (query.search() != null && !query.search().isBlank()) {
                String pattern = "%" + query.search().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("message")), pattern)
                ));
            }

            if (query.types() != null && !query.types().isEmpty()) {
                predicates.add(root.get("type").in(query.types()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
