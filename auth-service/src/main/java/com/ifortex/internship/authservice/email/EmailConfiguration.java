package com.ifortex.internship.authservice.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Getter
@Setter
@Configuration
public class EmailConfiguration {

  @Value("${spring.mail.username}")
  private String emailUsername;

  @Value("${spring.mail.password}")
  private String emailPassword;

  @Value("${spring.mail.host}")
  private String emailHost;

  @Value("${spring.mail.port}")
  private int emailPort;

  @Bean
  public JavaMailSender javaMailSender() {
    JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
    mailSender.setHost(emailHost);
    mailSender.setUsername(emailUsername);
    mailSender.setPassword(emailPassword);
    mailSender.setPort(emailPort);

    Properties properties = mailSender.getJavaMailProperties();
    properties.put("mail.transport.protocol", "smtp");
    properties.put("mail.smtp.auth", "true");
    properties.put("mail.smtp.starttls.enable", "true");
    //properties.put("mail.debug", "true");

    return mailSender;
  }
}
