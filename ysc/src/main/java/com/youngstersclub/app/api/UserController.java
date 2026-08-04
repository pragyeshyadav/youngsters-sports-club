package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.CreateCustomerRequest;
import com.youngstersclub.app.dto.CreateCustomerResponseDto;
import com.youngstersclub.app.dto.MergeUserAccountRequest;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.PhoneVerificationResponse;
import com.youngstersclub.app.dto.PlayerSummaryDto;
import com.youngstersclub.app.dto.UpdateCustomerRequest;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.dto.VerifyPhoneRequest;
import com.youngstersclub.app.dto.UserPhoneUpdateRequest;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.PlayerSummaryService;
import com.youngstersclub.app.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;

@RestController
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");
    private final UserRepository userRepository;
    private final UserService userService;
    private final OrganizationContextService organizationContextService;
    private final PlayerSummaryService playerSummaryService;

    public UserController(
            UserRepository userRepository,
            UserService userService,
            OrganizationContextService organizationContextService,
            PlayerSummaryService playerSummaryService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.organizationContextService = organizationContextService;
        this.playerSummaryService = playerSummaryService;
    }

    @GetMapping("/api/user")
    public ResponseEntity<User> getUser(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/users/search")
    public List<UserSearchResultDto> searchUsers(@RequestParam String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        String digitsQuery = normalizedQuery.replaceAll("\\D", "");
        if (normalizedQuery.length() < 3 && digitsQuery.length() < 3) {
            return List.of();
        }

        return userRepository.searchActiveUserSummaries(
                normalizedQuery,
                digitsQuery,
                PageRequest.of(0, 10));
    }

    @PostMapping("/api/user/phone")
    public ResponseEntity<String> updatePhone(@RequestBody UserPhoneUpdateRequest request) {
        Optional<User> optionalUser = userRepository.findByEmail(request.getEmail());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        User user = optionalUser.get();

        // If phone already exists -> do not update
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
            log.info("Phone update skipped - phone already exists for user: {}", request.getEmail());
            return ResponseEntity.ok("Phone already exists");
        }

        user.setPhone(request.getPhone());
        User savedUser = userRepository.save(user);
        if (request.getOrganizationId() != null && request.getBranchId() != null) {
            organizationContextService.ensureContextForResolvedUser(
                    savedUser,
                    request.getOrganizationId(),
                    request.getBranchId());
        }

        log.info("Phone number saved successfully for user: {}", request.getEmail());
        return ResponseEntity.ok("Phone number saved successfully");
    }

    @PostMapping("/api/user/verify-phone")
    public ResponseEntity<?> verifyPhone(@RequestBody VerifyPhoneRequest request) {
        String phoneNumber = request == null || request.getPhoneNumber() == null ? "" : request.getPhoneNumber().trim();
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Phone number must be exactly 10 digits"));
        }

        try {
            PhoneVerificationResponse response = userService.verifyPhone(phoneNumber);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/api/user/merge-account")
    public ResponseEntity<?> mergeAccount(@RequestBody MergeUserAccountRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Merge request is required"));
        }

        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String phoneNumber = request.getPhoneNumber() == null ? "" : request.getPhoneNumber().trim();

        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Current user email is required"));
        }
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Phone number must be exactly 10 digits"));
        }

        try {
            User mergedUser = userService.mergeUserAccounts(email, phoneNumber);
            if (request.getOrganizationId() != null && request.getBranchId() != null) {
                organizationContextService.ensureContextForResolvedUser(
                        mergedUser,
                        request.getOrganizationId(),
                        request.getBranchId());
            }
            return ResponseEntity.ok(mergedUser);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            log.warn("User merge failed for email {} and phone {}: {}", email, phoneNumber, ex.getMessage());
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected user merge failure for email {} and phone {}", email, phoneNumber, ex);
            return ResponseEntity.internalServerError().body(new MessageResponseDto("Unable to merge account right now"));
        }
    }

    @GetMapping("/api/users/player-summary")
    public ResponseEntity<org.springframework.data.domain.Page<PlayerSummaryDto>> getPlayerSummary(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        try {
            return ResponseEntity.ok(playerSummaryService.getPlayerSummaries(pageable, actorEmail));
        } catch (SecurityException ex) {
            return ResponseEntity.status(403).build();
        } catch (java.util.NoSuchElementException ex) {
            return ResponseEntity.status(404).build();
        }
    }

    @PostMapping("/api/users/create-customer")
    public ResponseEntity<?> createCustomer(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail,
            @RequestBody CreateCustomerRequest request) {
        try {
            CreateCustomerResponseDto response = userService.createManualCustomerInCurrentContext(request, actorEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(403).body(new MessageResponseDto(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(new MessageResponseDto(ex.getMessage()));
        } catch (java.util.NoSuchElementException ex) {
            return ResponseEntity.status(404).body(new MessageResponseDto(ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(409).body(new MessageResponseDto("Customer creation conflicted with an existing membership mapping"));
        }
    }

    @PutMapping("/api/customer/update")
    public ResponseEntity<?> updateCustomer(@RequestBody UpdateCustomerRequest request) {
        if (request == null || request.getUserId() == null) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Customer details are required"));
        }

        Optional<User> optionalUser = userRepository.findById(request.getUserId());
        if (optionalUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = optionalUser.get();
        String phone = request.getPhone() == null ? "" : request.getPhone().trim();

        if (!PHONE_PATTERN.matcher(phone).matches()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Phone number must be exactly 10 digits"));
        }

        Optional<User> existingPhoneUser = userRepository.findByPhone(phone);
        if (existingPhoneUser.isPresent() && !existingPhoneUser.get().getId().equals(user.getId())) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Customer with this mobile number already exists"));
        }

        boolean isManualUser = user.getGoogleId() != null && user.getGoogleId().startsWith("MANUAL_USER_");
        if (isManualUser) {
            String name = request.getName() == null ? "" : request.getName().trim();
            String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();

            if (name.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponseDto("Name is required"));
            }

            if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
                return ResponseEntity.badRequest().body(new MessageResponseDto("Enter a valid email address"));
            }

            if (!email.isEmpty()) {
                Optional<User> existingEmailUser = userRepository.findByEmail(email);
                if (existingEmailUser.isPresent() && !existingEmailUser.get().getId().equals(user.getId())) {
                    return ResponseEntity.badRequest().body(new MessageResponseDto("Customer with this email already exists"));
                }
                user.setEmail(email);
            }

            user.setName(name);
        }

        user.setPhone(phone);
        userRepository.save(user);
        return ResponseEntity.ok(user);
    }

}
