package com.ifortex.internship.authservice.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

  private final JavaMailSender emailSender;

  @Value("${spring.mail.username}")
  private String emailUsername;

  public void sendVerificationEmail(String to, String subject, String otp)
      throws MessagingException {

    log.debug("Sending email with subject '{}' to email: {}", subject, to);

    String template = loadEmailTemplate("verification-email.html");
    String htmlMessage = populateTemplate(template, otp);

    MimeMessage message = emailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);

    // feature I am not sure about first line
    helper.setFrom(emailUsername);
    helper.setTo(to);
    helper.setSubject(subject);
    helper.setText(htmlMessage, true);

    emailSender.send(message);

    log.debug("Email with OTP was send to email: {}", to);
  }

  private String loadEmailTemplate(String fileName) {
    try {
      Path path = new ClassPathResource("templates/" + fileName).getFile().toPath();
      return Files.readString(path);
    } catch (IOException e) {
      throw new RuntimeException("Failed to load email template: " + fileName, e);
    }
  }

  private String populateTemplate(String template, String otp) {
    return template.replace("{{otp}}", otp);
  }
}
