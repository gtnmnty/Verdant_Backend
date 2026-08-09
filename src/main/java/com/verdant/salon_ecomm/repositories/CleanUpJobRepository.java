package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.PendingCleanUpJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CleanUpJobRepository extends JpaRepository<PendingCleanUpJob, UUID> {

    List<PendingCleanUpJob> findByProcessedFalse();

    @Modifying
    @Query("UPDATE PendingCleanUpJob j SET j.processed = true WHERE j.id = :id")
    void markProcessed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PendingCleanUpJob j SET j.retryCount = j.retryCount + 1 WHERE j.id = :id")
    void incrementRetryCount(@Param("id") UUID id);
}