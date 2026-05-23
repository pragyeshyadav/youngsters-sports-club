package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PhoneVerificationResponse;
import com.youngstersclub.app.dto.UserPreviewDto;
import com.youngstersclub.app.dto.UserLoginRequest;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public String handleGoogleLogin(UserLoginRequest request) {
        Optional<User> existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isEmpty() && request.getGoogleId() != null) {
            existingUser = userRepository.findByGoogleId(request.getGoogleId());
        }

        if (existingUser.isPresent()) {
            log.info("User already exists: {}", request.getEmail());
            return "Welcome back! Thanks for being a valuable player of our club 🎱";
        } else {
            User newUser = new User();
            newUser.setName(request.getName());
            newUser.setEmail(request.getEmail());
            newUser.setGoogleId(request.getGoogleId());
            newUser.setProfilePic(request.getProfilePic());
            newUser.setRole(UserRole.CUSTOMER);
            newUser.setIsActive(true);

            userRepository.save(newUser);
            log.info("New user created: {}", request.getEmail());
            return "Welcome to Youngsters Sports Club 🎉";
        }
    }

    public PhoneVerificationResponse verifyPhone(String phoneNumber) {
        String normalizedPhone = normalizePhone(phoneNumber);
        if (normalizedPhone.isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        Optional<User> existingUser = userRepository.findByPhone(normalizedPhone);
        if (existingUser.isEmpty()) {
            return new PhoneVerificationResponse(false, null);
        }

        User user = existingUser.get();
        return new PhoneVerificationResponse(true, new UserPreviewDto(user.getId(), user.getName(), user.getPhone()));
    }

    @Transactional
    public User mergeUserAccounts(String currentUserEmail, String phoneNumber) {
        String normalizedEmail = currentUserEmail == null ? "" : currentUserEmail.trim().toLowerCase();
        String normalizedPhone = normalizePhone(phoneNumber);

        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Current user email is required");
        }
        if (normalizedPhone.isEmpty()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        User newUser = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Current user not found"));
        User existingUser = userRepository.findByPhone(normalizedPhone)
                .orElseThrow(() -> new IllegalArgumentException("Existing customer not found"));

        if (existingUser.getId().equals(newUser.getId())) {
            return existingUser;
        }

        boolean existingManualUser = isManualUser(existingUser);
        boolean existingActiveGoogleUser = !existingManualUser
                && Boolean.TRUE.equals(existingUser.getIsActive())
                && existingUser.getGoogleId() != null
                && !existingUser.getGoogleId().isBlank();

        if (existingActiveGoogleUser) {
            log.warn("Merge skipped because phone {} already belongs to active Google user {}", normalizedPhone, existingUser.getId());
            throw new IllegalStateException("This phone number is already linked to another Google account");
        }

        String originalGoogleId = newUser.getGoogleId();
        String originalEmail = newUser.getEmail();
        retireGoogleIdentity(newUser);
        newUser.setPhone(null);
        newUser.setIsActive(false);

        existingUser.setGoogleId(originalGoogleId);
        existingUser.setEmail(originalEmail);
        existingUser.setName(newUser.getName());
        existingUser.setProfilePic(newUser.getProfilePic());
        existingUser.setIsActive(true);
        existingUser.setPhone(normalizedPhone);

        userRepository.save(newUser);
        User mergedUser = userRepository.save(existingUser);
        log.info("Merged manual user {} into Google identity from user {}", existingUser.getId(), newUser.getId());
        return mergedUser;
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }

    private boolean isManualUser(User user) {
        return user.getGoogleId() != null && user.getGoogleId().startsWith("MANUAL_USER_");
    }

    private void retireGoogleIdentity(User user) {
        long marker = System.currentTimeMillis();
        String currentEmail = user.getEmail() == null ? "user" : user.getEmail().trim().toLowerCase().replaceAll("[^a-z0-9@._-]", "");
        String emailLocal = currentEmail.contains("@") ? currentEmail.substring(0, currentEmail.indexOf('@')) : currentEmail;
        if (emailLocal.isBlank()) {
            emailLocal = "user";
        }
        user.setEmail("merged_" + user.getId() + "_" + marker + "_" + emailLocal + "@merged.local");
        user.setGoogleId("MERGED_USER_" + user.getId() + "_" + marker);
    }

}
