package com.ifortex.internship.authservice.email;

import com.ifortex.internship.authservice.exception.custom.InternalServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender emailSender;

    @Value("${spring.mail.username}")
    private String emailUsername;

    public void sendVerificationEmail(String to, String subject, String otp)
        throws MessagingException {

        log.debug("Sending verification email with subject '{}' to: {}", subject, to);

        String template = loadEmailTemplate("verification-email.html");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("otp", otp);

        sendEmail(to, subject, template, replacements);
    }

    public void sendPasswordResetRequestEmail(String to, String subject, String resetLink)
        throws MessagingException {

        log.debug("Sending password reset email with subject '{}' to: {}", subject, to);

        String template = loadEmailTemplate("password-reset-request-email.html");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("reset_link", resetLink);
        sendEmail(to, subject, template, replacements);
    }

    private void sendEmail(
        String to, String subject, String template, Map<String, String> replacements)
        throws MessagingException {
        String htmlMessage = populateTemplate(template, replacements);

        MimeMessage message = emailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);

        helper.setFrom(emailUsername);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlMessage, true);

        emailSender.send(message);

        log.debug("Email was sent to email: {}", to);
    }

    private String loadEmailTemplate(String fileName) {
        try {
            Path path = new ClassPathResource("templates/" + fileName).getFile().toPath();
            return Files.readString(path);
        } catch (IOException e) {
            throw new InternalServiceException(
                String.format("Failed to load email template: %s. Details: %s", fileName, e.getMessage()));
        }
    }

    private String populateTemplate(String template, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}
