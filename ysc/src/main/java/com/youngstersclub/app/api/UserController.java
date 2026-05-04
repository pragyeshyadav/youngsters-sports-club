package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.CreateCustomerRequest;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.UserPhoneUpdateRequest;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/api/user")
    public ResponseEntity<User> getUser(@RequestParam String email) {
        return userRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/users/search")
    public List<User> searchUsers(@RequestParam String query) {
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            return List.of();
        }

        return userRepository.findTop10ByNameContainingIgnoreCaseOrderByNameAsc(normalizedQuery);
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
        userRepository.save(user);

        log.info("Phone number saved successfully for user: {}", request.getEmail());
        return ResponseEntity.ok("Phone number saved successfully");
    }

    @GetMapping("/api/users/player-summary")
    public ResponseEntity<org.springframework.data.domain.Page<com.youngstersclub.app.dto.PlayerSummaryProjection>> getPlayerSummary(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(userRepository.getPlayerSummaries(pageable));
    }

    @PostMapping("/api/users/create-customer")
    public ResponseEntity<?> createCustomer(@RequestBody CreateCustomerRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Customer details are required"));
        }

        String name = request.getName() == null ? "" : request.getName().trim();
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String mobileNumber = request.getMobileNumber() == null ? "" : request.getMobileNumber().trim();

        if (name.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Name is required"));
        }

        if (!email.isEmpty() && !EMAIL_PATTERN.matcher(email).matches()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Valid email is required"));
        }

        if (!PHONE_PATTERN.matcher(mobileNumber).matches()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Mobile number must be exactly 10 digits"));
        }

        if (email.isEmpty()) {
            email = buildDummyEmail(name, mobileNumber);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Customer with this email already exists"));
        }

        if (userRepository.findByPhone(mobileNumber).isPresent()) {
            return ResponseEntity.badRequest().body(new MessageResponseDto("Customer with this mobile number already exists"));
        }

        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setGoogleId("MANUAL_USER_" + mobileNumber);
        user.setPhone(mobileNumber);
        user.setRole(UserRole.CUSTOMER);
        user.setIsActive(true);
        userRepository.save(user);

        return ResponseEntity.ok(new MessageResponseDto("Customer created successfully"));
    }

    private String buildDummyEmail(String name, String mobileNumber) {
        String normalizedName = name == null
                ? "customer"
                : name.toLowerCase()
                        .trim()
                        .replaceAll("\\s+", "_")
                        .replaceAll("[^a-z0-9_]", "");

        if (normalizedName.isBlank()) {
            normalizedName = "customer";
        }

        return "dummy_" + normalizedName + "_" + mobileNumber + "@gmail.com";
    }
}
