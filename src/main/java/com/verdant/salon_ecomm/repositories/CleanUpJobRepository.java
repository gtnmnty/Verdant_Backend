package com.verdant.salon_ecomm.repositories;

import com.verdant.salon_ecomm.entities.PendingCleanUpJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CleanUpJobRepository extends JpaRepository<PendingCleanUpJob, UUID> {

    // Atomically claims up to `batchSize` unprocessed jobs that are either
    // unclaimed or whose lease has expired, stamping them with this
    // scheduler instance's id and a new expiry. FOR UPDATE SKIP LOCKED lets
    // concurrent instances run this same query without blocking on or
    // double-claiming rows another instance already grabbed.
    @Modifying
    @Query(value = """
        UPDATE pending_cleanup_jobs
        SET claimed_by = :instanceId, claim_expires_at = :expiresAt
        WHERE id IN (
            SELECT id FROM pending_cleanup_jobs
            WHERE processed = false
              AND (claimed_by IS NULL OR claim_expires_at < now())
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
        )
        """, nativeQuery = true)
    int claimBatch(
        @Param("instanceId") String instanceId,
        @Param("expiresAt") OffsetDateTime expiresAt,
        @Param("batchSize") int batchSize
    );

    // Fetch the jobs this instance just claimed, to actually process.
    List<PendingCleanUpJob> findByClaimedByAndProcessedFalse(String claimedBy);

    @Modifying
    @Query("UPDATE PendingCleanUpJob j SET j.processed = true, j.claimedBy = null, j.claimExpiresAt = null WHERE j.id = :id")
    void markProcessed(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE PendingCleanUpJob j SET j.retryCount = j.retryCount + 1, j.claimExpiresAt = null WHERE j.id = :id")
    void incrementRetryCount(@Param("id") UUID id);
}