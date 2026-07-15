package com.verdant.salon_ecomm.resolvers.reviews;

import com.verdant.salon_ecomm.dtos.reviews.AdminReviewDto;
import com.verdant.salon_ecomm.dtos.reviews.ReviewTargetInfo;
import com.verdant.salon_ecomm.dtos.reviews.ReviewTargetKey;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.models.enums.appointments.AppointmentServiceType;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
public class ReviewAdminResolver {

    @SchemaMapping(typeName = "AdminReviewDto", field = "itemName")
    public CompletableFuture<String> itemName(AdminReviewDto review,
                                              DataLoader<ReviewTargetKey, ReviewTargetInfo> reviewTargetInfoLoader) {
        return reviewTargetInfoLoader
            .load(new ReviewTargetKey(review.itemType(), review.targetId()))
            .thenApply(ReviewTargetInfo::itemName);
    }

    @SchemaMapping(typeName = "AdminReviewDto", field = "serviceType")
    public CompletableFuture<AppointmentServiceType> serviceType(AdminReviewDto review,
                                                                 DataLoader<ReviewTargetKey, ReviewTargetInfo> reviewTargetInfoLoader) {
        if (review.itemType() != ItemType.SALON_SERVICE) {
            return CompletableFuture.completedFuture(null);
        }
        return reviewTargetInfoLoader
            .load(new ReviewTargetKey(review.itemType(), review.targetId()))
            .thenApply(ReviewTargetInfo::serviceType);
    }

    @SchemaMapping(typeName = "AdminReviewDto", field = "serviceName")
    public CompletableFuture<String> serviceName(AdminReviewDto review,
                                                 DataLoader<ReviewTargetKey, ReviewTargetInfo> reviewTargetInfoLoader) {
        if (review.itemType() != ItemType.SALON_SERVICE) {
            return CompletableFuture.completedFuture(null);
        }
        return reviewTargetInfoLoader
            .load(new ReviewTargetKey(review.itemType(), review.targetId()))
            .thenApply(ReviewTargetInfo::serviceName);
    }
}