package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.reviews.ReviewConnection;
import com.verdant.salon_ecomm.dtos.reviews.ReviewDto;
import com.verdant.salon_ecomm.dtos.reviews.ReviewUserDto;
import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.ReviewRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.ReviewSpec;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private static final int REVIEWS_PAGE_SIZE = 3;

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final SalonServiceRepository salonServiceRepository;

  public ReviewConnection getReviews(ItemType targetType, UUID targetId, Short starsFilter, int page) {
    Specification<Review> spec = Specification.allOf(
        ReviewSpec.targetIs(targetType, targetId),
        ReviewSpec.starsEquals(starsFilter)
    );

    Pageable pageable = PageRequest.of(page, REVIEWS_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<Review> result = reviewRepository.findAll(spec, pageable);

    return new ReviewConnection(
        result.map(this::toDto).getContent(),
        result.getTotalElements(),
        result.getTotalPages(),
        page
    );
  }

  @Transactional
  public ReviewDto upsertReview(UUID userId, ItemType targetType, UUID targetId, short stars, String text) {
    if (targetType != ItemType.PRODUCT && targetType != ItemType.SALON_SERVICE) {
      throw new IllegalArgumentException("Reviews only support PRODUCT or SALON_SERVICE targets");
    }

    Review review = reviewRepository
        .findByUser_IdAndTargetTypeAndTargetId(userId, targetType, targetId)
        .orElseGet(() -> Review.builder()
            .user(userRepository.getReferenceById(userId))
            .targetType(targetType)
            .targetId(targetId)
            .build());

    review.setStars(stars);
    review.setText(text);
    Review saved = reviewRepository.save(review);

    updateAggregatesFor(targetType, targetId);

    return toDto(saved);
  }

  private void updateAggregatesFor(ItemType targetType, UUID targetId) {
    BigDecimal avg = reviewRepository.findAverageRatingByTargetId(targetId)
            .setScale(2, RoundingMode.HALF_UP);
    int count = reviewRepository.countByTargetId(targetId);

    int rowsUpdated = switch (targetType) {
      case PRODUCT -> productRepository.updateReviewAggregates(targetId, count, avg);
      case SALON_SERVICE -> salonServiceRepository.updateReviewAggregates(targetId, count, avg);
      default -> throw new IllegalStateException("Unreachable: validated in upsertReview");
    };

    if (rowsUpdated == 0) {
      throw new IllegalStateException("Failed to update aggregates — no matching " + targetType + " row for id " + targetId);
    }
  }

  private ReviewDto toDto(Review review) {
    User user = review.getUser();
    ReviewUserDto userDto = new ReviewUserDto(user.getId(), user.getFullName());

    return new ReviewDto(
        review.getId(),
        userDto,
        review.getStars(),
        review.getText(),
        review.getCreatedAt()
    );
  }


}