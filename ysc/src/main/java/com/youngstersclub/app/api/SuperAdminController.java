package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.CustomerOnboardingCandidateDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.SuperAdminBranchDto;
import com.youngstersclub.app.dto.SuperAdminBranchRequest;
import com.youngstersclub.app.dto.SuperAdminOrganizationDto;
import com.youngstersclub.app.dto.SuperAdminOrganizationRequest;
import com.youngstersclub.app.dto.SuperAdminPortalContextDto;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentRequest;
import com.youngstersclub.app.dto.SuperAdminStaffAssignmentResponseDto;
import com.youngstersclub.app.dto.SuperAdminUserSearchResultDto;
import com.youngstersclub.app.service.SuperAdminService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SuperAdminController {
  private final SuperAdminService superAdminService;

  public SuperAdminController(SuperAdminService superAdminService) {
    this.superAdminService = superAdminService;
  }

  @GetMapping("/api/super-admin/context")
  public ResponseEntity<?> getPortalContext(@RequestParam String email) {
    try {
      SuperAdminPortalContextDto response = superAdminService.getPortalContext(email);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/super-admin/organizations")
  public ResponseEntity<?> getOrganizations(@RequestParam String email) {
    try {
      List<SuperAdminOrganizationDto> response = superAdminService.getOrganizations(email);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PostMapping("/api/super-admin/organizations")
  public ResponseEntity<?> createOrganization(@RequestBody SuperAdminOrganizationRequest request) {
    return saveOrganization(null, request);
  }

  @PutMapping("/api/super-admin/organizations/{organizationId}")
  public ResponseEntity<?> updateOrganization(
      @PathVariable Long organizationId,
      @RequestBody SuperAdminOrganizationRequest request) {
    return saveOrganization(organizationId, request);
  }

  @PostMapping("/api/super-admin/organizations/{organizationId}/deactivate")
  public ResponseEntity<?> deactivateOrganization(
      @PathVariable Long organizationId,
      @RequestParam String email) {
    try {
      superAdminService.deactivateOrganization(organizationId, email);
      return ResponseEntity.ok(new MessageResponseDto("Organization deactivated successfully"));
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/super-admin/branches")
  public ResponseEntity<?> getBranches(@RequestParam String email, @RequestParam Long organizationId) {
    try {
      List<SuperAdminBranchDto> response = superAdminService.getBranches(email, organizationId);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PostMapping("/api/super-admin/branches")
  public ResponseEntity<?> createBranch(@RequestBody SuperAdminBranchRequest request) {
    return saveBranch(null, request);
  }

  @PutMapping("/api/super-admin/branches/{branchId}")
  public ResponseEntity<?> updateBranch(
      @PathVariable Long branchId,
      @RequestBody SuperAdminBranchRequest request) {
    return saveBranch(branchId, request);
  }

  @PostMapping("/api/super-admin/branches/{branchId}/deactivate")
  public ResponseEntity<?> deactivateBranch(
      @PathVariable Long branchId,
      @RequestParam Long organizationId,
      @RequestParam String email) {
    try {
      superAdminService.deactivateBranch(branchId, organizationId, email);
      return ResponseEntity.ok(new MessageResponseDto("Branch deactivated successfully"));
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/super-admin/users/search")
  public ResponseEntity<?> searchCustomers(
      @RequestParam String email,
      @RequestParam Long organizationId,
      @RequestParam String query) {
    try {
      List<SuperAdminUserSearchResultDto> response =
          superAdminService.searchCustomers(email, organizationId, query);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/super-admin/users/{userId}/assignments")
  public ResponseEntity<?> getUserAssignments(@PathVariable Integer userId, @RequestParam String email) {
    try {
      CustomerOnboardingCandidateDto response = superAdminService.getUserAssignments(email, userId);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PostMapping("/api/super-admin/staff-assignment")
  public ResponseEntity<?> upsertStaffAssignment(@RequestBody SuperAdminStaffAssignmentRequest request) {
    try {
      SuperAdminStaffAssignmentResponseDto response = superAdminService.upsertStaffAssignment(request);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  private ResponseEntity<?> saveOrganization(Long organizationId, SuperAdminOrganizationRequest request) {
    try {
      SuperAdminOrganizationDto response = organizationId == null
          ? superAdminService.createOrganization(request)
          : superAdminService.updateOrganization(organizationId, request);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  private ResponseEntity<?> saveBranch(Long branchId, SuperAdminBranchRequest request) {
    try {
      SuperAdminBranchDto response = branchId == null
          ? superAdminService.createBranch(request)
          : superAdminService.updateBranch(branchId, request);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }
}
