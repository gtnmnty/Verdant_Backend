package com.verdant.salon_ecomm.services.events.listeners.account;

import com.verdant.salon_ecomm.dtos.account.events.AccountEmailChangedEvent;
import com.verdant.salon_ecomm.services.AuthenticationService;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEmailChangeListener {

    private final EmailService emailService;

    // Runs after commit and off the request thread, so a slow SMTP server can't stall the admin's mutation.
    // Each send has its own try: a failed notice to the old address must not stop the code reaching the new one.
    // Catches Exception, not just MessagingException: JavaMailSender.send throws the unchecked MailException.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onEmailChanged(AccountEmailChangedEvent e) {
        try {
            emailService.sendEmailChangedNoticeEmail(e.oldEmail(), e.newEmail(), true);
        } catch (Exception ex) {
            log.error("Failed to send email-changed notice for account {}", e.accountId(), ex);
        }
        try {
            // Not the signup template: that one says "Welcome" and claims a 5-minute expiry.
            emailService.sendEmailChangeCodeEmail(
                e.newEmail(), e.verificationCode(), AuthenticationService.EMAIL_CHANGE_CODE_TTL_MINUTES);
        } catch (Exception ex) {
            // The user can recover through resend-verification, so this isn't a dead end.
            log.error("Failed to send verification email for account {}", e.accountId(), ex);
        }
    }
}