package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuperAdminAuthorizationServiceTest {

  @Mock private UserRepository userRepository;

  @InjectMocks private SuperAdminAuthorizationService authorizationService;

  private User activeUser;

  @BeforeEach
  void setUp() {
    activeUser = new User();
    activeUser.setId(9);
    activeUser.setEmail("admin@example.com");
    activeUser.setIsActive(true);
    activeUser.setRole(UserRole.ADMIN);
  }

  @Test
  void requireSuperAdminAllowsLegacySuperAdminUserId() {
    activeUser.setId(2);
    when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.of(activeUser));

    User resolved = authorizationService.requireSuperAdmin(" Legacy@Example.com ");

    assertSame(activeUser, resolved);
  }

  @Test
  void requireSuperAdminAllowsExplicitSuperAdminRole() {
    activeUser.setRole(UserRole.SUPER_ADMIN);
    when(userRepository.findByEmail("super@example.com")).thenReturn(Optional.of(activeUser));

    User resolved = authorizationService.requireSuperAdmin("super@example.com");

    assertSame(activeUser, resolved);
  }

  @Test
  void requireSuperAdminRejectsNormalActiveUser() {
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(activeUser));

    SecurityException exception =
        assertThrows(SecurityException.class, () -> authorizationService.requireSuperAdmin("admin@example.com"));

    assertEquals("You are not authorized to access the Super Admin Portal", exception.getMessage());
  }

  @Test
  void isSuperAdminReturnsFalseForInactiveUser() {
    activeUser.setId(2);
    activeUser.setIsActive(false);

    assertFalse(authorizationService.isSuperAdmin(activeUser));
  }

  @Test
  void normalizeEmailTrimsAndLowerCases() {
    assertEquals("owner@example.com", authorizationService.normalizeEmail(" Owner@Example.com "));
    assertTrue(authorizationService.isSuperAdmin(buildUser(2, UserRole.CUSTOMER, true)));
    assertTrue(authorizationService.isSuperAdmin(buildUser(9, UserRole.SUPER_ADMIN, true)));
  }

  private User buildUser(Integer id, UserRole role, boolean active) {
    User user = new User();
    user.setId(id);
    user.setRole(role);
    user.setIsActive(active);
    return user;
  }
}
