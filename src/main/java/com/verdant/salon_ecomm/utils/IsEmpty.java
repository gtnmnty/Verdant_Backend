package com.verdant.salon_ecomm.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Consumer;

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
                        cloudinaryDelete.accept(publicId);
                    }
                }
            }
        );
    }
}