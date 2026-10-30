package com.verdant.salon_ecomm.integration;

import com.verdant.salon_ecomm.AbstractIntegrationTest;
import com.verdant.salon_ecomm.dtos.reviews.ReviewDto;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.models.entities.MediaImage;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.accounts.AccountRole;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.services.MediaImageService;
import com.verdant.salon_ecomm.services.ReviewService;
import com.verdant.salon_ecomm.support.Concurrently;
import com.verdant.salon_ecomm.support.Concurrently.Outcome;
import com.verdant.salon_ecomm.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// M-31/V10: one review per user per item, and aggregates that don't lose updates under parallel reviews.
// M-40/V14: at most one primary image per item, however many setPrimary calls race.
class ReviewAndPrimaryImageRaceTest extends AbstractIntegrationTest {

    @Autowired ReviewService reviewService;
    @Autowired MediaImageService mediaImageService;
    @Autowired ProductRepository productRepository;
    @Autowired SalonServiceRepository salonServiceRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestData data;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private int reviewRows(UUID userId, UUID targetId) {
        return jdbc.queryForObject("select count(*) from reviews where user_id = ? and target_id = ?",
            Integer.class, userId, targetId);
    }

    // ── reviews ──────────────────────────────────────────

    @Test
    void parallelSubmissionsByOneUserLeaveOneReview() throws Exception {
        Product product = data.product(1);
        User user = data.customer();

        List<Outcome<ReviewDto>> outcomes = Concurrently.run(6, i -> () ->
            reviewService.upsertReview(user.getId(), ItemType.PRODUCT, product.getId(), (short) (i % 5 + 1), "r" + i));

        // The row lock serializes them: later ones see the first review and get a 409 (one review per user);
        // a unique-index hit would also be a 409, never a 500.
        assertThat(outcomes).filteredOn(o -> !o.succeeded())
            .allSatisfy(o -> assertThat(o.error()).isInstanceOf(BusinessRuleException.class));
        assertThat(reviewRows(user.getId(), product.getId())).isEqualTo(1);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getReviewCount()).isEqualTo(1);
    }

    @Test
    void parallelReviewsByDifferentUsersAreAllCounted() throws Exception {
        Product product = data.product(1);
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 6; i++) users.add(data.customer());

        // Stars 1..6 capped at 5: 1,2,3,4,5,5 -> average 20/6 = 3.33
        List<Outcome<ReviewDto>> outcomes = Concurrently.run(6, i -> () ->
            reviewService.upsertReview(users.get(i).getId(), ItemType.PRODUCT, product.getId(),
                (short) Math.min(i + 1, 5), null));

        assertThat(Concurrently.successes(outcomes)).isEqualTo(6);
        Product after = productRepository.findById(product.getId()).orElseThrow();
        assertThat(after.getReviewCount()).isEqualTo(6);
        assertThat(after.getAverageRating()).isEqualByComparingTo("3.33");
    }

    @Test
    void salonServicesCanBeReviewed() {
        SalonService service = data.salonService();
        User user = data.customer();

        reviewService.upsertReview(user.getId(), ItemType.SALON_SERVICE, service.getId(), (short) 4, "Great cut");

        assertThat(reviewRows(user.getId(), service.getId())).isEqualTo(1);
        assertThat(salonServiceRepository.findById(service.getId()).orElseThrow().getReviewCount()).isEqualTo(1);
    }

    // ── primary image ────────────────────────────────────

    @Test
    void racingSetPrimaryCallsLeaveExactlyOnePrimary() throws Exception {
        Product product = data.product(1);
        User admin = data.user(AccountRole.ADMIN, TestData.uniqueEmail());
        List<MediaImage> images = new ArrayList<>();
        for (int i = 0; i < 4; i++) images.add(data.image(ItemType.PRODUCT, product.getId(), i == 0, i));

        List<Outcome<Object>> outcomes = Concurrently.run(6, i -> () -> {
            TestData.signInAs(admin);
            try {
                return mediaImageService.setPrimary(ItemType.PRODUCT, images.get(i % 4).getId(), product.getId(),
                    admin.getId());
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        assertThat(Concurrently.successes(outcomes)).isEqualTo(6);
        assertThat(jdbc.queryForObject(
            "select count(*) from media_images where entity_id = ? and is_primary", Integer.class, product.getId()))
            .isEqualTo(1);
    }

    @Test
    void databaseRejectsASecondPrimaryEvenIfTheAppIsBypassed() {
        Product product = data.product(1);
        data.image(ItemType.PRODUCT, product.getId(), true, 0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> data.image(ItemType.PRODUCT, product.getId(), true, 1))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
