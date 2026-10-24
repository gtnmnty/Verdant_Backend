package com.verdant.salon_ecomm.services.events.listeners;

import com.verdant.salon_ecomm.dtos.user.events.UserEmailChangeRequestedEvent;
import com.verdant.salon_ecomm.dtos.user.events.UserEmailChangedEvent;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Mail for the self-service email change. After commit and @Async, like the other mail listeners,
// so a slow SMTP server never holds up the mutation. Logs carry the user id only, never addresses or codes.
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEmailChangeListener {

    private final EmailService emailService;

    // Step 1: send the confirmation code to the address the user wants to switch to.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChangeRequested(UserEmailChangeRequestedEvent e) {
        try {
            emailService.sendEmailChangeCodeEmail(
                e.newEmail(), e.verificationCode(), AuthenticationService.EMAIL_CHANGE_CODE_TTL_MINUTES);
        } catch (Exception ex) {
            // Recoverable: the user can submit the change again to get a fresh code.
            log.error("Failed to send email-change code for user {}", e.userId(), ex);
        }
    }

    // Step 2 done: tell the OLD address, which is the only warning a hijacked account owner would get.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChanged(UserEmailChangedEvent e) {
        try {
            emailService.sendEmailChangedNoticeEmail(e.oldEmail(), e.newEmail(), false);
        } catch (Exception ex) {
            log.error("Failed to send email-changed notice for user {}", e.userId(), ex);
        }
    }
}
