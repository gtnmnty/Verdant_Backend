package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.reviews.*;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.entities.SalonService;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import com.verdant.salon_ecomm.models.enums.reviews.AdminReviewSort;
import com.verdant.salon_ecomm.models.enums.reviews.ReviewClientSort;
import com.verdant.salon_ecomm.models.enums.reviews.ReviewsClientFilter;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import com.verdant.salon_ecomm.repositories.ReviewRepository;
import com.verdant.salon_ecomm.repositories.SalonServiceRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.specifications.ReviewSpec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ProductRepository productRepository;
  private final SalonServiceRepository salonServiceRepository;

  // ---------- Customer-facing ----------

  public ReviewConnection getReviews(
      ItemType targetType, UUID targetId, ReviewsClientFilter filter,
      ReviewClientSort sort, int page, int pageSize
  ) {
    Specification<Review> spec = Specification.allOf(
        ReviewSpec.targetIs(targetType, targetId),
        ReviewSpec.starsEquals(toStars(filter))
    );

    int normalizedPageSize = Math.max(pageSize, 1);
    Pageable pageable = PageRequest.of(page, normalizedPageSize, toClientSort(sort));
    Page<Review> result = reviewRepository.findAll(spec, pageable);

    return new ReviewConnection(
        result.map(this::toDto).getContent(),
        (int) result.getTotalElements(),
        result.getTotalPages(),
        page
    );
  }

  @Transactional
  public ReviewDto upsertReview(UUID userId, ItemType targetType, UUID targetId, short stars, String text) {
    if (targetType != ItemType.PRODUCT && targetType != ItemType.SALON_SERVICE) {
      throw new IllegalArgumentException("Reviews only support PRODUCT or SALON_SERVICE targets");
    }

    if (stars < 1 || stars > 5) {
      throw new IllegalArgumentException("stars must be between 1 and 5");
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

  // ---------- Admin-facing ----------

  public AdminReviewPage getAdminReviews(
      ItemType itemType, ReviewsClientFilter filter, String search,
      AdminReviewSort sort, int page, int pageSize
  ) {
    int normalizedPage = Math.max(page, 1);
    int normalizedPageSize = Math.max(pageSize, 1);

    Specification<Review> spec = Specification.allOf(
        ReviewSpec.hasItemType(itemType),
        ReviewSpec.starsEquals(toStars(filter)),
        ReviewSpec.matchesSearch(search)
    );

    Pageable pageable = PageRequest.of(normalizedPage - 1, normalizedPageSize, toAdminSort(sort));
    Page<Review> result = reviewRepository.findAll(spec, pageable);

    List<AdminReviewDto> items = result.getContent().stream()
        .map(this::toAdminDto)
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
        .orElseThrow(() -> new EntityNotFoundException("Review not found: " + id));
    return toAdminDto(review);
  }
  // ---------- Shared internals ----------

  private void updateAggregatesFor(ItemType targetType, UUID targetId) {
    BigDecimal avg = reviewRepository.findAverageRatingByTargetId(targetType, targetId)
        .setScale(2, RoundingMode.HALF_UP);
    int count = reviewRepository.countByTargetId(targetType, targetId);

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

  // itemName/serviceType/serviceName aren't stored on Review — it only has
  // targetType/targetId, so they're resolved here by looking up the actual
  // Product or SalonService each time. If this list gets large/frequently
  // paginated, consider batching these lookups instead of one-by-one.
  private AdminReviewDto toAdminDto(Review review) {
    User user = review.getUser();
    ReviewUserDto userDto = new ReviewUserDto(user.getId(), user.getFullName());

    ItemType itemType = review.getTargetType();
    String itemName;
    AppointmentServiceType serviceType = null;
    String serviceName = null;

    if (itemType == ItemType.SALON_SERVICE) {
      SalonService service = salonServiceRepository.findById(review.getTargetId())
          .orElseThrow(() -> new EntityNotFoundException("Service not found: " + review.getTargetId()));
      itemName = service.getName();
      serviceName = service.getName();
      serviceType = Boolean.TRUE.equals(service.getIsHomeService())
          ? AppointmentServiceType.HOME_SERVICE
          : AppointmentServiceType.IN_SALON;
    } else if (itemType == ItemType.PRODUCT) {
      Product product = productRepository.findById(review.getTargetId())
          .orElseThrow(() -> new EntityNotFoundException("Product not found: " + review.getTargetId()));
      itemName = product.getName();
    } else {
      throw new IllegalStateException("Unsupported review target type: " + itemType);
    }

    return new AdminReviewDto(
        review.getId(),
        userDto,
        itemType,
        itemName,
        serviceType,
        serviceName,
        review.getStars(),
        review.getText(),
        review.getCreatedAt()
    );
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