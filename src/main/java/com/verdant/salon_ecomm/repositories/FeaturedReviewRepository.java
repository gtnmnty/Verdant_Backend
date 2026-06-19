package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.FeaturedReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeaturedReviewRepository extends JpaRepository<FeaturedReview, UUID> {
}