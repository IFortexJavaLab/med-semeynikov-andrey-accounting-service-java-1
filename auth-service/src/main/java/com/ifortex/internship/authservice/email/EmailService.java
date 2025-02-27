package com.ifortex.internship.authservice.email;

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

    /**
     * Sends a verification email containing a one-time password (OTP) to the specified email address.
     *
     * @param to      the recipient's email address
     * @param subject the subject of the email
     * @param otp     the one-time password (OTP) to be included in the email body
     * @throws MessagingException if an error occurs while sending the email
     */
    public void sendVerificationEmail(String to, String subject, String otp)
            throws MessagingException {

        log.debug("Sending verification email with subject '{}' to: {}", subject, to);

        String template = loadEmailTemplate("verification-email.html");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("otp", otp);

        sendEmail(to, subject, template, replacements);
    }

    /**
     * Sends a password reset request email with a link for the recipient to reset their password.
     *
     * @param to        the recipient's email address
     * @param subject   the subject of the email
     * @param resetLink the link to reset the password
     * @throws MessagingException if an error occurs while sending the email
     */
    public void sendPasswordResetRequestEmail(String to, String subject, String resetLink)
            throws MessagingException {

        log.debug("Sending password reset email with subject '{}' to: {}", subject, to);

        String template = loadEmailTemplate("password-reset-request-email.html");

        Map<String, String> replacements = new HashMap<>();
        replacements.put("reset_link", resetLink);
        sendEmail(to, subject, template, replacements);
    }

    /**
     * Sends an email to the specified recipient with a given subject and HTML body template,
     * replacing placeholders with specified values.
     *
     * @param to           the recipient's email address
     * @param subject      the subject of the email
     * @param template     the HTML email template
     * @param replacements a map containing the placeholder values to replace in the template
     * @throws MessagingException if an error occurs while sending the email
     */
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

    /**
     * Loads an email template from the classpath.
     *
     * @param fileName the name of the email template file
     * @return the content of the email template as a string
     * @throws RuntimeException if an error occurs while reading the template file
     */
    private String loadEmailTemplate(String fileName) {
        try {
            Path path = new ClassPathResource("templates/" + fileName).getFile().toPath();
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load email template: " + fileName, e);
        }
    }

    /**
     * Replaces placeholders in the email template with actual values.
     *
     * @param template the email template with placeholders
     * @param values   a map of placeholder values
     * @return the email template with placeholders replaced by the corresponding values
     */
    private String populateTemplate(String template, Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }
}
