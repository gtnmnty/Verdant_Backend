package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.Review;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

//public interface ReviewRepository extends JpaRepository<Review, UUID> {
//    List<Review> findByProductId(UUID productId);
//    List<Review> findByServiceId(UUID serviceId);
//    List<Review> findByUserId(UUID userId);
//    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.productId = :productId")
//    Double findAverageRatingByProductId(@Param("productId") UUID productId);
//    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.serviceId = :serviceId")
//    Double findAverageRatingByServiceId(@Param("serviceId") UUID serviceId);
//    int countByProductId(UUID productId);
//    int countByServiceId(UUID serviceId);
//}