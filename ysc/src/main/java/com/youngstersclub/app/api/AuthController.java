package com.youngstersclub.app.api;

import com.youngstersclub.app.mail.EmailService;
import com.youngstersclub.app.dto.UserLoginRequest;
import com.youngstersclub.app.service.UserService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final EmailService emailService;
  private final UserService userService;

  public AuthController(EmailService emailService, UserService userService) {
    this.emailService = emailService;
    this.userService = userService;
  }

  @PostMapping("/google")
  public ResponseEntity<Map<String, String>> googleLogin(@RequestBody Map<String, String> body) {
    String name = body.getOrDefault("name", "").trim();
    String email = body.getOrDefault("email", "").trim();
    if (!name.isEmpty() && !email.isEmpty()) {
      emailService.sendLoginNotification(name, email);
    }
    return ResponseEntity.ok(Map.of("status", "ok"));
  }

  @PostMapping("/google-login")
  public ResponseEntity<String> login(@RequestBody UserLoginRequest request) {
    String message = userService.handleGoogleLogin(request);
    return ResponseEntity.ok(message);
  }
}
