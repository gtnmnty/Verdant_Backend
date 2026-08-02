package com.verdant.salon_ecomm.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class IsEmpty {

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