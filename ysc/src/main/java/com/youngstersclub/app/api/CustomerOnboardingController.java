package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.CustomerOnboardingCandidateDto;
import com.youngstersclub.app.dto.CustomerOnboardingContextDto;
import com.youngstersclub.app.dto.CustomerOnboardingRequest;
import com.youngstersclub.app.dto.CustomerOnboardingResponseDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.service.CustomerOnboardingService;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomerOnboardingController {

  private final CustomerOnboardingService customerOnboardingService;

  public CustomerOnboardingController(CustomerOnboardingService customerOnboardingService) {
    this.customerOnboardingService = customerOnboardingService;
  }

  @GetMapping("/api/manager/customer-onboarding/context")
  public ResponseEntity<?> getOnboardingContext(
      @RequestParam String email,
      @RequestParam(required = false) Long organizationId) {
    try {
      CustomerOnboardingContextDto response =
          customerOnboardingService.getOnboardingContext(email, organizationId);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException | IllegalStateException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @GetMapping("/api/manager/customer-onboarding/customer")
  public ResponseEntity<?> getCandidate(@RequestParam Integer userId) {
    try {
      CustomerOnboardingCandidateDto response = customerOnboardingService.getCandidateDetails(userId);
      return ResponseEntity.ok(response);
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }

  @PostMapping("/api/manager/customer-onboarding")
  public ResponseEntity<?> onboardCustomer(@RequestBody CustomerOnboardingRequest request) {
    try {
      CustomerOnboardingResponseDto response = customerOnboardingService.onboardCustomer(request);
      return ResponseEntity.ok(response);
    } catch (SecurityException ex) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
    } catch (IllegalStateException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDto(ex.getMessage()));
    } catch (DataIntegrityViolationException ex) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(new MessageResponseDto("Customer onboarding conflicted with an existing membership mapping"));
    } catch (NoSuchElementException ex) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
    }
  }
}
