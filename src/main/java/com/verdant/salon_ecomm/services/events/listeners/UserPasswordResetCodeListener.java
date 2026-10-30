package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.user.events.UserPasswordResetCodeRequestedEvent;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Mail for the public forgot-password form. After commit and @Async, like the other mail listeners:
// SMTP took several seconds inline, and that extra time only happened for registered addresses, so
// the response time revealed which emails have accounts. Logs carry the user id only.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPasswordResetCodeListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResetCodeRequested(UserPasswordResetCodeRequestedEvent e) {
        try {
            emailService.sendPasswordResetCodeEmail(e.email(), e.resetCode());
        } catch (Exception ex) {
            // Recoverable: the user can press "Resend Code" to get a fresh one.
            log.error("Failed to send password reset code for user {}", e.userId(), ex);
        }
    }
}
