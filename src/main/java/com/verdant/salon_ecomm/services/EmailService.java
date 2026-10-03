package com.verdant.salon_ecomm.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
public class EmailService {

    private final JavaMailSender emailSender;
    private final SpringTemplateEngine templateEngine;
    private final String fromEmail;

    public EmailService(
            JavaMailSender emailSender,
            SpringTemplateEngine templateEngine,
            @Value("${spring.mail.username}") String fromEmail
    ) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.fromEmail = fromEmail;
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

    /** Gift card delivery. All values are rendered with th:text, so user-supplied name/note are HTML-escaped. */
    public void sendGiftCardEmail(String to, String recipientName, String senderName, String amountText,
                                  String code, String note, String expiresText) throws MessagingException {
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

    /** Sent when an account is deleted and its remaining gift card balance is reissued as a new card. */
    public void sendGiftCardRefundEmail(String to, String amountText, String code) throws MessagingException {
        Context context = new Context();
        context.setVariable("amountText", amountText);
        context.setVariable("code", code);

        String htmlBody = templateEngine.process("emails/gift-card-refund", context);
        sendEmail(to, "Your Verdant Salon gift card balance", htmlBody);
    }

    private void sendEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage mimeMessage = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        emailSender.send(mimeMessage);
    }
}