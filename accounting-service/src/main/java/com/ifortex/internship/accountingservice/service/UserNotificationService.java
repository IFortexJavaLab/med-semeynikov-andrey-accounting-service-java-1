package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.medstarter.emailservice.EmailService;
import jakarta.mail.MessagingException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class UserNotificationService {

    static final String OTP = "otp";
    static final String VERIFICATION_EMAIL_TEMPLATE = "verification-email.html";
    static final String PASSWORD_RESET_REQUEST_EMAIL_TEMPLATE = "password-reset-request-email.html";

    EmailService emailService;

    public void sendVerificationEmail(String to, String subject, String otp) throws MessagingException {
        log.debug("Sending verification email with subject '{}' to: {}", subject, to);

        String template = emailService.loadEmailTemplate(VERIFICATION_EMAIL_TEMPLATE);
        Map<String, String> replacements = new HashMap<>();
        replacements.put(OTP, otp);
        String content = emailService.populateTemplate(template, replacements);

        emailService.sendEmail(to, subject, content, true);
    }

    public void sendPasswordResetRequestEmail(String to, String subject, String resetLink) throws MessagingException {
        log.debug("Sending password reset email with subject '{}' to: {}", subject, to);

        String template = emailService.loadEmailTemplate(PASSWORD_RESET_REQUEST_EMAIL_TEMPLATE);
        Map<String, String> replacements = new HashMap<>();
        replacements.put("reset_link", resetLink);
        String content = emailService.populateTemplate(template, replacements);

        emailService.sendEmail(to, subject, content, true);

    }
}

