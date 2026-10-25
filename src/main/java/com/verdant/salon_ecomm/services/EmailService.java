package com.verdant.salon_ecomm.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    private final JavaMailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromEmail;
    private final String frontendUrl;

    // Shown in the newsletter emails. Change it here if the public brand name differs.
    private static final String BRAND_NAME = "Verdant Luxe";

    public EmailService(
            JavaMailSender emailSender,
            SpringTemplateEngine templateEngine,
            // app.mail.from is optional: bulk providers (SendGrid, Brevo, SES...) log in with a username that is
            // NOT a valid From address, so set app.mail.from when you switch. Falls back to the old behavior.
            @Value("${app.mail.from:${spring.mail.username}}") String fromEmail,
            @Value("${app.frontend-url:${FRONTEND_URL:http://localhost:3000}}") String frontendUrl
    ) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    //Link placed in every newsletter. The frontend page at this path must call unsubscribeFromNewsletter(token).
    public String unsubscribeUrl(String token) {
        return frontendUrl + "/newsletter/unsubscribe" + (token == null ? "" : "?token=" + token);
    }

    // Link in staff-issued reset emails (H-9). The frontend page at this path must read ?token= and
    // call redeemPasswordReset(token, newPassword). The token is Base64-URL, so it needs no escaping.
    public String passwordResetUrl(String token) {
        return frontendUrl + "/reset-password?token=" + token;
    }

    public void sendNewsletterWelcomeEmail(String to, String unsubscribeUrl) throws MessagingException {
        Context context = new Context();
        context.setVariable("brandName", BRAND_NAME);
        context.setVariable("shopUrl", frontendUrl);
        context.setVariable("unsubscribeUrl", unsubscribeUrl);

        String htmlBody = templateEngine.process("emails/newsletter-welcome", context);
        sendEmail(
            to,
            "Welcome to " + BRAND_NAME,
            htmlBody,
            Map.of("List-Unsubscribe", "<" + unsubscribeUrl + ">")
        );
    }

    // One newsletter to one recipient. paragraphs and every other value are HTML-escaped by the template.
    public void sendNewsletterEmail(
        String to, String subject, List<String> paragraphs,
        String ctaLabel, String ctaUrl, String unsubscribeUrl
    ) throws MessagingException {
        Context context = new Context();
        context.setVariable("brandName", BRAND_NAME);
        context.setVariable("paragraphs", paragraphs);
        context.setVariable("ctaLabel", ctaLabel);
        context.setVariable("ctaUrl", ctaUrl);
        context.setVariable("unsubscribeUrl", unsubscribeUrl);

        String htmlBody = templateEngine.process("emails/newsletter", context);
        sendEmail(to, subject, htmlBody, Map.of("List-Unsubscribe", "<" + unsubscribeUrl + ">"));
    }

    public void sendVerificationEmail(String to, String verificationCode) throws MessagingException {
        Context context = new Context();
        context.setVariable("verificationCode", verificationCode);

        String htmlBody = templateEngine.process("emails/verification", context);
        sendEmail(to, "Account Verification Code", htmlBody);
    }

    public void sendPasswordResetCodeEmail(String to, String resetCode) throws MessagingException {
        Context context = new Context();
        context.setVariable("resetCode", resetCode);

        String htmlBody = templateEngine.process("emails/password-reset", context);
        sendEmail(to, "Your Password Reset Code", htmlBody);
    }

    // Staff-issued reset link (H-9). newAccount switches the wording: a staff-created account has never
    // had a password, so it reads "set your password" instead of "reset". Different from
    // sendPasswordResetCodeEmail, which is the 6-digit code from the public forgot-password form.
    public void sendPasswordResetLinkEmail(String to, String resetUrl, Duration ttl, boolean newAccount)
            throws MessagingException {
        Context context = new Context();
        context.setVariable("brandName", BRAND_NAME);
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiresText", describe(ttl));
        context.setVariable("newAccount", newAccount);

        String htmlBody = templateEngine.process("emails/password-reset-link", context);
        sendEmail(to, newAccount ? "Set up your " + BRAND_NAME + " account" : "Reset your password", htmlBody);
    }

    // Gift card delivery. All values are rendered with th:text, so user-supplied name/note are HTML-escaped.
    public void sendGiftCardEmail(
        String to, String recipientName, String senderName, String amountText,
        String code, String note, String expiresText
    ) throws MessagingException {
        Context context = new Context();
        context.setVariable("recipientName", recipientName);
        context.setVariable("senderName", senderName);
        context.setVariable("amountText", amountText);
        context.setVariable("code", code);
        context.setVariable("note", note);
        context.setVariable("expiresText", expiresText);

        String htmlBody = templateEngine.process("emails/gift-card", context);
        sendEmail(to, "You've received a Verdant Salon gift card", htmlBody);
    }

    // Sent when an account is deleted and its remaining gift card balance is reissued as a new card.
    public void sendGiftCardRefundEmail(String to, String amountText, String code) throws MessagingException {
        Context context = new Context();
        context.setVariable("amountText", amountText);
        context.setVariable("code", code);

        String htmlBody = templateEngine.process("emails/gift-card-refund", context);
        sendEmail(to, "Your Verdant Salon gift card balance", htmlBody);
    }

    // "24 hours", "1 hour", "45 minutes": the link lifetime differs for new accounts, so the email can't hardcode it.
    private static String describe(Duration ttl) {
        long hours = ttl.toHours();
        if (hours >= 1 && ttl.equals(Duration.ofHours(hours))) return hours + (hours == 1 ? " hour" : " hours");
        long minutes = ttl.toMinutes();
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    private void sendEmail(String to, String subject, String htmlBody) throws MessagingException {
        sendEmail(to, subject, htmlBody, Map.of());
    }

    private void sendEmail(String to, String subject, String htmlBody, Map<String, String> headers)
            throws MessagingException {
        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            mimeMessage.setHeader(header.getKey(), header.getValue());
        }

        emailSender.send(mimeMessage);
    }

    // Sent to the OLD address after an email change, so the real owner can spot a hijack.
    // changedByStaff switches the wording: staff changes also sign the user out everywhere.
    public void sendEmailChangedNoticeEmail(String to, String newEmail, boolean changedByStaff)
            throws MessagingException {
        Context context = new Context();
        context.setVariable("newEmail", newEmail);
        context.setVariable("changedByStaff", changedByStaff);
        String htmlBody = templateEngine.process("emails/email-changed", context);
        sendEmail(to, "Your account email was changed", htmlBody);
    }

    // Code that proves the user controls a new address (self-service change or a staff change).
    public void sendEmailChangeCodeEmail(String to, String verificationCode, int expiresInMinutes)
            throws MessagingException {
        Context context = new Context();
        context.setVariable("verificationCode", verificationCode);
        context.setVariable("expiresInMinutes", expiresInMinutes);
        String htmlBody = templateEngine.process("emails/email-change-code", context);
        sendEmail(to, "Confirm your new email address", htmlBody);
    }
}