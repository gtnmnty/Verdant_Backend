package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.user.events.UserDeletedEvent;
import com.verdant.salon_ecomm.models.entities.PendingCleanUpJob;
import com.verdant.salon_ecomm.repositories.CleanUpJobRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

// Moved out of UserService so the scheduled job is its own bean, like GiftCardSweepJob and
// UnpaidOrderExpiryJob. UserService only depended on PaymentService for this job.
//
// Flow:
//   1. UserService.deleteUserById anonymizes the user and saves a PendingCleanUpJob row in the
//      same transaction, so the external cleanup is recorded only if the deletion commits.
//   2. Every 60s this job claims a batch of unprocessed rows (claim token + lease, see
//      CleanUpJobTransactions). Another instance, or an overlapping run, can't claim the same rows.
//   3. For each row: delete the Cloudinary avatar and the Stripe customer, mark the row processed,
//      then publish UserDeletedEvent (audit + notifications).
//   4. A failure bumps retryCount. The lease expires and the row is retried on a later run, up to
//      CLEANUP_MAX_RETRIES, then left for manual cleanup.
@Slf4j
@Component
@RequiredArgsConstructor
public class DeletedUserCleanupJob {

    // Stable per-JVM identity used to claim cleanup jobs, so only this
    // instance processes the batch it claimed. Doesn't need to survive
    // restarts — an unfinished claim just expires and gets picked up by
    // whichever instance runs the next scheduled pass.
    private final String instanceId = UUID.randomUUID().toString();
    private static final int CLEANUP_BATCH_SIZE = 50;
    private static final long CLEANUP_LEASE_MINUTES = 5;
    // A job that keeps failing (e.g. bad credentials, a permanently broken asset) stops being
    // claimed after this many attempts instead of being retried every minute forever.
    private static final int CLEANUP_MAX_RETRIES = 10;

    private final CleanUpJobRepository cleanUpJobRepository;
    private final CleanUpJobTransactions cleanUpJobTransactions;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelay = 60_000)
    public void processPendingCleanupJobs() {
        // Unique per invocation (not the fixed per-JVM instanceId) so two overlapping
        // runs of this method never share a claim token and can't collide on the same batch.
        String claimToken = instanceId + ":" + UUID.randomUUID();
        cleanUpJobTransactions.claimBatch(claimToken, CLEANUP_LEASE_MINUTES, CLEANUP_BATCH_SIZE, CLEANUP_MAX_RETRIES);

        for (PendingCleanUpJob job : cleanUpJobRepository.findByClaimedByAndProcessedFalse(claimToken)) {
            try {
                // External calls run outside any DB transaction — no point holding
                // a DB connection/transaction open for the duration of an HTTP call.
                if (job.getAvatarPublicId() != null) {
                    cloudinaryService.delete(job.getAvatarPublicId());
                }
                if (job.getStripeCustomerId() != null) {
                    paymentService.deleteStripeCustomer(job.getStripeCustomerId());
                }
                cleanUpJobTransactions.markProcessed(job.getId(), claimToken);

                userRepository.findById(job.getUserId())
                    .ifPresent(user -> eventPublisher.publishEvent(new UserDeletedEvent(user)));
            } catch (Exception e) {
                cleanUpJobTransactions.incrementRetryCount(job.getId(), claimToken);
                int attempts = job.getRetryCount() + 1;
                log.error("Cleanup job {} failed for user {} (attempt {} of {})",
                    job.getId(), job.getUserId(), attempts, CLEANUP_MAX_RETRIES, e);
                if (attempts >= CLEANUP_MAX_RETRIES) {
                    log.error("Cleanup job {} for user {} reached the retry limit and will not run again; "
                        + "clean up manually", job.getId(), job.getUserId());
                }
            }
        }
    }
}
