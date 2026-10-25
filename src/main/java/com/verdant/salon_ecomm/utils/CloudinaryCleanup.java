package com.verdant.salon_ecomm.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class CloudinaryCleanup {

    // Runs the deletes only once the surrounding transaction commits (H-14), so a rolled-back delete
    // never leaves DB rows pointing at removed files. Must be called inside an active @Transactional
    // method: registerSynchronization throws IllegalStateException without one.
    // Failures are logged per file and never undo the commit; at worst a file is orphaned.
    public static void scheduleCloudinaryDeletion(List<String> publicIds, Consumer<String> cloudinaryDelete) {
        if (publicIds == null || publicIds.isEmpty()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String publicId : publicIds) {
                        try {
                            cloudinaryDelete.accept(publicId);
                        } catch (Exception ex) {
                            log.error("Failed to delete Cloudinary asset '{}' after commit", publicId, ex);
                        }
                    }
                }
            }
        );
    }
}