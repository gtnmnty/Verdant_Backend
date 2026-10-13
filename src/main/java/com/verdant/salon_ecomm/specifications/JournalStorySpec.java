package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.models.entities.journal.JournalStory;
import com.verdant.salon_ecomm.models.entities.journal.JournalStoryProduct;
import com.verdant.salon_ecomm.models.enums.journal.JournalCategory;
import com.verdant.salon_ecomm.models.enums.journal.JournalStoryStatus;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

//Every method returns null when its filter is absent, so they compose with Specification.allOf(...).
public class JournalStorySpec {

    public static Specification<JournalStory> hasStatus(JournalStoryStatus status) {
        if (status == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    public static Specification<JournalStory> hasCategory(JournalCategory category) {
        if (category == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("category"), category);
    }

    // Same fields the page searched client-side: client name, service, stylist, and product names.
    public static Specification<JournalStory> matchesSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = "%" + search.trim().toLowerCase() + "%";

        return (root, query, cb) -> {
            // EXISTS subquery for product names: no join on the main query,
            // so no duplicate rows and paging stays correct.
            assert query != null;
            Subquery<Integer> productMatch = query.subquery(Integer.class);
            Root<JournalStoryProduct> link = productMatch.from(JournalStoryProduct.class);
            productMatch.select(cb.literal(1)).where(
                cb.equal(link.get("story"), root),
                cb.like(cb.lower(link.get("product").get("name")), pattern)
            );

            return cb.or(
                cb.like(cb.lower(root.get("clientName")), pattern),
                cb.like(cb.lower(root.get("service")), pattern),
                cb.like(cb.lower(root.get("stylist")), pattern),
                cb.exists(productMatch)
            );
        };
    }
}
