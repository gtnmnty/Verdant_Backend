package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.reviews.ReviewConnection;
import com.verdant.salon_ecomm.dtos.reviews.ReviewDto;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ReviewResolver {

  private final ReviewService reviewService;

  @QueryMapping
  public ReviewConnection reviews(@Argument ItemType targetType, @Argument UUID targetId,
                                  @Argument Integer starsFilter, @Argument int page) {
    return reviewService.getReviews(targetType, targetId,
            starsFilter != null ? starsFilter.shortValue() : null, page);
  }

  @MutationMapping
  @PreAuthorize("isAuthenticated()")
  public ReviewDto upsertReview(@Argument ItemType targetType, @Argument UUID targetId,
                                @Argument int stars, @Argument String text) {
    return reviewService.upsertReview(getCurrentUserId(), targetType, targetId, (short) stars, text);
  }

  private UUID getCurrentUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof User user) {
      return user.getId();
    }
    throw new IllegalStateException("No authenticated user found");
  }
}