package com.youngstersclub.app.service;

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
}
