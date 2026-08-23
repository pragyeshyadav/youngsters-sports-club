package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.OrganizationContextChangeRequest;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.service.OrganizationContextService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationContextController {

  private final OrganizationContextService organizationContextService;

  public OrganizationContextController(OrganizationContextService organizationContextService) {
    this.organizationContextService = organizationContextService;
  }

  @GetMapping("/api/context")
  public ResponseEntity<?> getContext(@RequestParam String email) {
    try {
      OrganizationContextDto response = organizationContextService.resolveContext(email);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/organizations")
  public ResponseEntity<?> getOrganizations(@RequestParam String email) {
    try {
      List<OrganizationOptionDto> response = organizationContextService.getAvailableOrganizations(email);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/branches")
  public ResponseEntity<?> getBranches(
      @RequestParam String email,
      @RequestParam Long organizationId) {
    try {
      List<BranchOptionDto> response =
          organizationContextService.getBranchesForOrganization(email, organizationId);
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PostMapping("/api/context/change")
  public ResponseEntity<?> changeContext(@RequestBody OrganizationContextChangeRequest request) {
    if (request == null || request.getEmail() == null || request.getEmail().trim().isEmpty()) {
      return ResponseEntity.badRequest().body(new MessageResponseDto("Email is required"));
    }
    if (request.getOrganizationId() == null || request.getBranchId() == null) {
      return ResponseEntity.badRequest().body(new MessageResponseDto("Organization and branch are required"));
    }

    try {
      OrganizationContextDto response = organizationContextService.changeContext(
          request.getEmail(), request.getOrganizationId(), request.getBranchId());
      return ResponseEntity.ok(response);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    }
  }
}
