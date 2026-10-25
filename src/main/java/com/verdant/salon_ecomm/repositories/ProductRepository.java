package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.dtos.product.ProductStockLevel;
import com.verdant.salon_ecomm.models.entities.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    // M-15: the stock queries are the only way stock moves for orders. Both also bump the @Version, so an
    // admin edit (ProductService.updateProduct) that loaded the product before this commit fails with a 409
    // instead of writing its stale stock_quantity back. JPQL bulk updates never bump it on their own.
    // Safe for the order paths: they never modify the Product entity after these calls, so no versioned
    // UPDATE of a now-stale entity follows in the same transaction.
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :qty, p.version = p.version + 1 " +
        "where p.id = :id and :qty > 0 and p.stockQuantity >= :qty")
    int decrementStock(@Param("id") UUID id, @Param("qty") int qty);

    // No version bump: the review aggregates are never written by the admin form, and @DynamicUpdate on
    // Product keeps an admin edit from writing these two columns back.
    @Modifying
    @Query("UPDATE Product p SET p.reviewCount = :count, p.averageRating = :avg WHERE p.id = :id")
    int updateReviewAggregates(@Param("id") UUID id, @Param("count") int count, @Param("avg") BigDecimal avg);

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity, p.version = p.version + 1 " +
        "WHERE p.id = :productId")
    void incrementStock(@Param("productId") UUID productId, @Param("quantity") int quantity);

    // M-29: fresh stock straight from the database (not the persistence context), for the stock alert event
    // OrderService publishes after decrementStock / incrementStock.
    @Query("SELECT new com.verdant.salon_ecomm.dtos.product.ProductStockLevel(p.id, p.name, p.stockQuantity, p.lowStockThreshold) " +
        "FROM Product p WHERE p.id = :id")
    Optional<ProductStockLevel> findStockLevel(@Param("id") UUID id);

    // M-32 / M-40: SELECT ... FOR UPDATE on the product row. Used as a per-product mutex: review submissions
    // (one review per user, consistent rating aggregates) and image changes (one primary image, image limit)
    // for the same product run one after another. Product is @Version'd; locking doesn't bump the version.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") UUID id);
}
