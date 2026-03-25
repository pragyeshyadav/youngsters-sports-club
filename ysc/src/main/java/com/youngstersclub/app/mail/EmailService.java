package com.youngstersclub.app.mail;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);

  private static final String LOGIN_NOTIFY_TO = "pragyesh.yadav@gmail.com";

  private final JavaMailSender mailSender;

  @Value("${spring.mail.username:}")
  private String mailUsername;

  public EmailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  /**
   * Sends login notification via Gmail SMTP. Failures are swallowed so login always succeeds.
   * Requires {@code spring.mail.username} + App Password in {@code spring.mail.password} (not the normal Gmail password).
   */
  public void sendLoginNotification(String name, String email) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(LOGIN_NOTIFY_TO);
      message.setSubject("New User Login - Youngsters Sports Club");
      message.setText(
          "New user logged in:\n\n"
              + "Name: "
              + name
              + "\n"
              + "Email: "
              + email
              + "\n"
              + "Time: "
              + LocalDateTime.now());

      if (mailUsername != null && !mailUsername.isBlank() && !isUnconfiguredUsername(mailUsername)) {
        message.setFrom(mailUsername);
      }

      mailSender.send(message);
      log.info("Email sent successfully (login notify for {})", email);
    } catch (Exception e) {
      log.warn("Email failed: {} — login is unaffected; use Gmail App Password if you see 535-5.7.8", e.getMessage());
    }
  }

  private static boolean isUnconfiguredUsername(String username) {
    String u = username.trim().toLowerCase();
    return u.equals("yourgmail@gmail.com") || u.startsWith("your_") || u.equals("yourgmail");
  }
}
