package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.policy.OrganizationPolicy;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.OrganizationPolicyService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationPolicyController {
  private final OrganizationContextService contextService;
  private final OrganizationPolicyService policyService;

  public OrganizationPolicyController(
      OrganizationContextService contextService,
      OrganizationPolicyService policyService) {
    this.contextService = contextService;
    this.policyService = policyService;
  }

  @GetMapping("/api/organization/policy")
  public ResponseEntity<?> getPolicy(@RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
    try {
      Long organizationId = resolveAdminOrganizationId(actorEmail);
      return ResponseEntity.ok(policyService.getEffectivePolicy(organizationId));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PutMapping("/api/organization/policy")
  public ResponseEntity<?> updatePolicy(
      @RequestHeader(name = "X-User-Email", required = false) String actorEmail,
      @RequestBody OrganizationPolicy policy) {
    try {
      Long organizationId = resolveAdminOrganizationId(actorEmail);
      return ResponseEntity.ok(policyService.updatePolicy(organizationId, policy));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  private Long resolveAdminOrganizationId(String actorEmail) {
    if (actorEmail == null || actorEmail.isBlank()) {
      throw new SecurityException("Authenticated user email is required");
    }
    var context = contextService.resolveContext(actorEmail);
    String role = context.getCurrentRole() == null ? "" : context.getCurrentRole().toUpperCase(Locale.ROOT);
    if (!"ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
      throw new SecurityException("Only admins can manage organization policy");
    }
    if (context.getCurrentOrganization() == null) {
      throw new IllegalArgumentException("Current organization context is required");
    }
    return context.getCurrentOrganization().getId();
  }
}
