package com.verdant.salon_ecomm.services.event_Listeners.newsletter;

import com.verdant.salon_ecomm.dtos.newsletter.NewsletterSubscribedEvent;
import com.verdant.salon_ecomm.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Welcome email: after the signup commits and off the request thread, so a slow mail server never delays the form. */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterEmailListener {

    private final EmailService emailService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubscribed(NewsletterSubscribedEvent event) {
        try {
            emailService.sendNewsletterWelcomeEmail(event.email(), emailService.unsubscribeUrl(event.unsubscribeToken()));
        } catch (Exception e) {
            // Never log the address. The subscription itself already succeeded; only the welcome note is lost.
            log.warn("Newsletter welcome email could not be sent", e);
        }
    }
}
