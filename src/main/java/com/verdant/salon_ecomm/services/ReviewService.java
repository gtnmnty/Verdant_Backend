package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.reviews.*;
import com.verdant.salon_ecomm.dtos.reviews.events.ReviewSubmittedEvent;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.Review;
import com.verdant.salon_ecomm.models.entities.SalonService;
import com.verdant.salon_ecomm.mappers.ReviewMapper;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.reviews.AdminReviewSort;
import com.verdant.salon_ecomm.models.enums.reviews.ReviewClientSort;
import com.verdant.salon_ecomm.models.enums.reviews.ReviewsClientFilter;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.ReviewRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.ReviewSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.verdant.salon_ecomm.utils.PageRequests;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SalonServiceRepository salonServiceRepository;
    private final ReviewMapper reviewMapper;
    private final ApplicationEventPublisher eventPublisher;

    // M-32: reviews are rendered publicly; this caps the text.
    private static final int MAX_REVIEW_TEXT = 2000;

    // ---------- Customer-facing ----------

    public ReviewConnection getReviews(
        ItemType targetType, UUID targetId, ReviewsClientFilter filter,
        ReviewClientSort sort, int page, int pageSize
    ) {
        Specification<Review> spec = Specification.allOf(
            ReviewSpec.targetIs(targetType, targetId),
            ReviewSpec.starsEquals(toStars(filter))
        );

        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toClientSort(sort));
        Page<Review> result = reviewRepository.findAll(spec, pageable);

        return new ReviewConnection(
            result.map(reviewMapper::toDto).getContent(),
            (int) result.getTotalElements(),
            result.getTotalPages(),
            normalizedPage
        );
    }

    // M-32 flow: validate -> lock the product/service row (SELECT ... FOR UPDATE) -> find this user's review
    // -> insert or update it -> flush -> recompute the aggregates -> commit releases the lock.
    // The lock serializes every review of the same target, which fixes two races at once:
    //   - two parallel submissions by one user both found no review and both inserted (duplicate reviews)
    //   - two parallel recomputes could each write a count/average that missed the other's review
    // The unique index (V10) is the backstop; hitting it becomes a 409 instead of a 500.
    @Transactional
    public ReviewDto upsertReview(UUID userId, ItemType targetType, UUID targetId, short stars, String text) {
        if (targetType != ItemType.PRODUCT && targetType != ItemType.SALON_SERVICE) {
            throw BusinessRuleException.invalid("Reviews only support PRODUCT or SALON_SERVICE targets");
        }

        if (stars < 1 || stars > 5) {
            throw BusinessRuleException.invalid("stars must be between 1 and 5");
        }

        // M-32: the text is shown publicly on the product/service page; it had no limit at all.
        String cleanText = text == null ? null : text.trim();
        if (cleanText != null && cleanText.length() > MAX_REVIEW_TEXT) {
            throw BusinessRuleException.invalid("Review text can be at most " + MAX_REVIEW_TEXT + " characters.");
        }

        String targetName = lockTargetAndGetName(targetType, targetId);

        Optional<Review> existing = reviewRepository
            .findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId);
        boolean isNewReview = existing.isEmpty();

        Review review = existing.orElseGet(() -> Review.builder()
            .user(userRepository.getReferenceById(userId))
            .targetType(targetType)
            .targetId(targetId)
            .build());

        review.setStars(stars);
        review.setText(cleanText);
        Review saved;
        try {
            // Flushed here so the aggregate queries below count this review, and so a unique-index hit
            // surfaces inside this try instead of at commit.
            saved = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw BusinessRuleException.conflict("You have already reviewed this. Refresh the page and edit your review.");
        }

        updateAggregatesFor(targetType, targetId);

        eventPublisher.publishEvent(new ReviewSubmittedEvent(saved, isNewReview, targetName));

        return reviewMapper.toDto(saved);
    }

    // ---------- Admin-facing ----------

    public AdminReviewPage getAdminReviews(
        ItemType itemType, ReviewsClientFilter filter, String search,
        AdminReviewSort sort, int page, int pageSize
    ) {
        int normalizedPage = PageRequests.page(page);
        int normalizedPageSize = PageRequests.size(pageSize, PageRequests.DEFAULT_MAX_SIZE);

        Specification<Review> spec = Specification.allOf(
            ReviewSpec.hasItemType(itemType),
            ReviewSpec.starsEquals(toStars(filter)),
            ReviewSpec.matchesSearch(search)
        );

        Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toAdminSort(sort));
        Page<Review> result = reviewRepository.findAll(spec, pageable);

        List<AdminReviewDto> items = result.getContent().stream()
            .map(reviewMapper::toAdminDto)
            .toList();

        return new AdminReviewPage(
            items,
            normalizedPage,
            normalizedPageSize,
            (int) result.getTotalElements(),
            result.getTotalPages()
        );
    }

    public AdminReviewDto getAdminReviewById(UUID id) {
        Review review = reviewRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Review not found: " + id));
        return reviewMapper.toAdminDto(review);
    }
    // ---------- Shared internals ----------

    private void updateAggregatesFor(ItemType targetType, UUID targetId) {
        BigDecimal avg = reviewRepository.findAverageRatingByTargetId(targetType, targetId)
            .setScale(2, RoundingMode.HALF_UP);
        int count = reviewRepository.countByTargetId(targetType, targetId);

        int rowsUpdated = switch (targetType) {
            case PRODUCT -> productRepository.updateReviewAggregates(targetId, count, avg);
            case SALON_SERVICE -> salonServiceRepository.updateReviewAggregates(targetId, count, avg);
        };

        if (rowsUpdated == 0) {
            throw BusinessRuleException.conflict(
                "Failed to update aggregates — no matching " + targetType + " row for id " + targetId
            );
        }
    }

    // M-32: locks the reviewed product/service row for the rest of the transaction and returns its name (for
    // the staff notification). An unknown target is now refused before anything is written; before, the review
    // was saved first and the aggregate update failed afterwards.
    private String lockTargetAndGetName(ItemType targetType, UUID targetId) {
        return switch (targetType) {
            case PRODUCT -> productRepository.findByIdForUpdate(targetId).map(Product::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
            case SALON_SERVICE -> salonServiceRepository.findByIdForUpdate(targetId).map(SalonService::getName)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
        };
    }

    private Short toStars(ReviewsClientFilter filter) {
        if (filter == null) return null;
        return switch (filter) {
            case FIVE_STARS -> (short) 5;
            case FOUR_STARS -> (short) 4;
            case THREE_STARS -> (short) 3;
            case TWO_STARS -> (short) 2;
            case ONE_STARS -> (short) 1;
        };
    }

    private Sort toClientSort(ReviewClientSort sort) {
        ReviewClientSort effective = sort != null ? sort : ReviewClientSort.MOST_RECENT;
        return switch (effective) {
            case MOST_RECENT -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case HIGHEST_RATED -> Sort.by(Sort.Direction.DESC, "stars");
            case LOWEST_RATED -> Sort.by(Sort.Direction.ASC, "stars");
        };
    }

    private Sort toAdminSort(AdminReviewSort sort) {
        AdminReviewSort effective = sort != null ? sort : AdminReviewSort.NEWEST;
        return switch (effective) {
            case NEWEST -> Sort.by(Sort.Direction.DESC, "createdAt");
            case OLDEST -> Sort.by(Sort.Direction.ASC, "createdAt");
            case CUSTOMER_NAME_ASC -> Sort.by(Sort.Direction.ASC, "user.fullName");
            case CUSTOMER_NAME_DESC -> Sort.by(Sort.Direction.DESC, "user.fullName");
            case RATING_HIGH_TO_LOW -> Sort.by(Sort.Direction.DESC, "stars");
            case RATING_LOW_TO_HIGH -> Sort.by(Sort.Direction.ASC, "stars");
        };
    }
}