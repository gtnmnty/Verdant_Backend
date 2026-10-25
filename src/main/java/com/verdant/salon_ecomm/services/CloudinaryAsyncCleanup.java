package com.verdant.salon_ecomm.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

// M-26: deletes a Cloudinary file that is no longer referenced (e.g. the avatar a user just replaced),
// with retries, on an async thread. The retry loop (with Thread.sleep backoff) used to run in
// UserService on the request thread, so a Cloudinary hiccup added up to 3 seconds to the response.
// Must be called through the Spring proxy (from another bean), or @Async does nothing.
// After the last failed attempt the file is only logged as orphaned: it costs storage, nobody sees it.
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudinaryAsyncCleanup {

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_DELAY_MS = 1000;

    private final CloudinaryService cloudinaryService;

    @Async
    public void deleteWithRetry(String publicId) {
        long delayMs = INITIAL_DELAY_MS;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                cloudinaryService.delete(publicId);
                return;
            } catch (Exception e) {
                log.warn("Cloudinary delete failed for {} (attempt {}/{})", publicId, attempt, MAX_ATTEMPTS, e);
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Giving up deleting Cloudinary file {} after {} attempts (orphaned)", publicId, MAX_ATTEMPTS, e);
                    return;
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                delayMs *= 2;
            }
        }
    }
}
