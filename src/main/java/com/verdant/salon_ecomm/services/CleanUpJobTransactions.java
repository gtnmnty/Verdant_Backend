package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.repositories.CleanUpJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

// Split out from UserService so each step commits in its own short transaction
// through Spring's proxy (calling these as this.xyz() from inside UserService
// would bypass the @Transactional interceptor entirely — self-invocation isn't
// proxied). Keeping the claim, mark-processed, and retry-increment steps here
// also means none of them share a transaction with the external Cloudinary/Stripe
// calls in UserService#processPendingCleanupJobs, so we're never holding a DB
// connection open for the duration of an HTTP call.
@Service
@RequiredArgsConstructor
public class CleanUpJobTransactions {

    private final CleanUpJobRepository cleanUpJobRepository;

    @Transactional
    public void claimBatch(String claimToken, long leaseMinutes, int batchSize) {
        OffsetDateTime leaseExpiry = OffsetDateTime.now().plusMinutes(leaseMinutes);
        cleanUpJobRepository.claimBatch(claimToken, leaseExpiry, batchSize);
    }

    @Transactional
    public void markProcessed(UUID jobId, String claimToken) {
        cleanUpJobRepository.markProcessed(jobId, claimToken);
    }

    @Transactional
    public void incrementRetryCount(UUID jobId, String claimToken) {
        cleanUpJobRepository.incrementRetryCount(jobId, claimToken);
    }
}
