package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.user.events.UserVerificationCodeRequestedEvent;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Mail for the sign-up verification code and "resend code". After commit and @Async, like the other mail
// listeners: SMTP took several seconds inline. Logs carry the user id only, never the address or the code.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserVerificationCodeListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerificationCodeRequested(UserVerificationCodeRequestedEvent e) {
        try {
            emailService.sendVerificationEmail(e.email(), e.verificationCode());
        } catch (Exception ex) {
            // Recoverable: the verify screen's "Resend Code" issues a fresh code and tries again.
            log.error("Failed to send verification code for user {}", e.userId(), ex);
        }
    }
}
