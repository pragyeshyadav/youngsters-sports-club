package com.youngstersclub.app.service;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class SuperAdminAuthorizationService {
  private static final int LEGACY_SUPER_ADMIN_USER_ID = 2;

  private final UserRepository userRepository;

  public SuperAdminAuthorizationService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public User requireSuperAdmin(String email) {
    User actor = userRepository.findByEmail(normalizeEmail(email))
        .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
        .orElseThrow(() -> new NoSuchElementException("Actor not found"));
    if (!isSuperAdmin(actor)) {
      throw new SecurityException("You are not authorized to access the Super Admin Portal");
    }
    return actor;
  }

  public boolean isSuperAdmin(User user) {
    if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
      return false;
    }
    return (user.getId() != null && user.getId() == LEGACY_SUPER_ADMIN_USER_ID)
        || user.getRole() == UserRole.SUPER_ADMIN;
  }

  protected String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }
}
