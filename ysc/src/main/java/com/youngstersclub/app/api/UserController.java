package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.UserPhoneUpdateRequest;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
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
}
