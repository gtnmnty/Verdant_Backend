package com.verdant.salon_ecomm.services.events.listeners.account;

import com.verdant.salon_ecomm.dtos.account.events.AccountPasswordResetRequestedEvent;
import com.verdant.salon_ecomm.models.enums.accounts.ResetLinkStatus;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.EmailService;
import com.verdant.salon_ecomm.services.PasswordResetTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.UUID;

// H-9: delivers the reset token issued by AccountService.createAccount / sendPasswordReset.
// Before this listener existed, nothing read event.resetToken(), so staff-created accounts could
// never set a password and "send password reset" sent nothing.
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountPasswordResetEmailListener {

    private final EmailService emailService;
    private final UserRepository userRepository;

    // AFTER_COMMIT: the token row must exist before anyone can click the link, and a rolled-back
    // account creation must not email a link for an account that doesn't exist.
    // @Async: a slow SMTP server can't stall the admin's mutation.
    // The outcome is written to the account (SENT / FAILED) so an admin can see a failed send on the
    // account page and press "Reset password" again (that also burns the old token). There is no
    // automatic retry; an outbox table plus a job would add that, at the cost of a table and a job.
    // SENT means the mail server accepted the message, not that it reached the inbox.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetRequested(AccountPasswordResetRequestedEvent event) {
        try {
            emailService.sendPasswordResetLinkEmail(
                event.email(),
                emailService.passwordResetUrl(event.resetToken()),
                PasswordResetTokenService.ttlFor(event.newAccount()),
                event.newAccount()
            );
            recordStatus(event.accountId(), ResetLinkStatus.SENT);
        } catch (Exception ex) {
            // Account id only: never log the token, the link or the address it went to.
            // Catches Exception, not just MessagingException: JavaMailSender.send throws the unchecked MailException.
            log.error("Failed to send password reset link for account {}", event.accountId(), ex);
            recordStatus(event.accountId(), ResetLinkStatus.FAILED);
        }
    }

    // A failure to record the status must not hide the real send result, so it is only logged.
    private void recordStatus(UUID accountId, ResetLinkStatus status) {
        try {
            userRepository.updateResetLinkStatus(accountId, status, OffsetDateTime.now());
        } catch (Exception ex) {
            log.error("Could not record reset link status {} for account {}", status, accountId, ex);
        }
    }
}
