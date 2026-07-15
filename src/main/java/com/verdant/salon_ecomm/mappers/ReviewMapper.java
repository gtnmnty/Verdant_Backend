package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.reviews.AdminReviewDto;
import com.verdant.salon_ecomm.dtos.reviews.ReviewDto;
import com.verdant.salon_ecomm.dtos.reviews.ReviewUserDto;
import com.verdant.salon_ecomm.entities.Review;
import com.verdant.salon_ecomm.entities.User;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewDto toDto(Review review) {
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

    // itemName / serviceType / serviceName are deliberately left null here.
    // They're resolved by AdminReviewFieldResolver via a shared DataLoader,
    // keyed on targetType+targetId, only when the client actually selects
    // those fields — see ReviewTargetKey / ReviewTargetInfo / the batch
    // loader registration in ReviewBatchLoaderConfig.
    public AdminReviewDto toAdminDto(Review review) {
        User user = review.getUser();
        ReviewUserDto userDto = new ReviewUserDto(user.getId(), user.getFullName());

        return new AdminReviewDto(
            review.getId(),
            userDto,
            review.getTargetType(),
            review.getTargetId(),
            null,
            null, // serviceType — resolved by AdminReviewFieldResolver
            null, // serviceName — resolved by AdminReviewFieldResolver
            review.getStars(),
            review.getText(),
            review.getCreatedAt()
        );
    }
}