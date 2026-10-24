package com.verdant.salon_ecomm.specifications;

import com.verdant.salon_ecomm.models.entities.newsletter.NewsletterSubscriber;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterSource;
import com.verdant.salon_ecomm.models.enums.newsletter.NewsletterStatus;
import org.springframework.data.jpa.domain.Specification;

// Every method returns null when its filter is absent, so they compose with Specification.allOf(...).
public class NewsletterSubscriberSpec {

    public static Specification<NewsletterSubscriber> hasStatus(NewsletterStatus status) {
        if (status == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("status"), status);
    }

    public static Specification<NewsletterSubscriber> hasSource(NewsletterSource source) {
        if (source == null) return null;
        return (root, query, cb) ->
            cb.equal(root.get("source"), source);
    }

    public static Specification<NewsletterSubscriber> emailContains(String search) {
        if (search == null || search.isBlank()) return null;
        String pattern = LikePatterns.contains(search);
        return (root, query, cb) ->
            cb.like(cb.lower(root.get("email")), pattern, LikePatterns.ESCAPE);
    }
}
