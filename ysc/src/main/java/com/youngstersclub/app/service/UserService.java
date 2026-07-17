package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.CreateCustomerRequest;
import com.youngstersclub.app.dto.CreateCustomerResponseDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.PhoneVerificationResponse;
import com.youngstersclub.app.dto.UserPreviewDto;
import com.youngstersclub.app.dto.UserLoginRequest;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final BranchRepository branchRepository;

    public UserService(
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            BranchRepository branchRepository) {
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.branchRepository = branchRepository;
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

    @Transactional
    public CreateCustomerResponseDto createManualCustomerInCurrentContext(
            CreateCustomerRequest request,
            String actorEmail) {
        ManualCustomerContext actorContext = resolveManualCustomerContext(actorEmail);

        String name = request == null || request.getName() == null ? "" : request.getName().trim();
        String email = request == null || request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();
        String mobileNumber = normalizePhone(request == null ? null : request.getMobileNumber());

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Name is required");
        }
        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Valid email is required");
        }
        if (!mobileNumber.matches("^[0-9]{10}$")) {
            throw new IllegalArgumentException("Mobile number must be exactly 10 digits");
        }

        String resolvedEmail = email.isEmpty() ? buildDummyEmail(name, mobileNumber) : email;
        if (userRepository.findByEmail(resolvedEmail).isPresent()) {
            throw new IllegalStateException("Customer with this email already exists");
        }
        if (userRepository.findByPhone(mobileNumber).isPresent()) {
            throw new IllegalStateException("Customer with this mobile number already exists");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(resolvedEmail);
        user.setGoogleId("MANUAL_USER_" + mobileNumber);
        user.setPhone(mobileNumber);
        user.setRole(UserRole.CUSTOMER);
        user.setIsActive(true);
        userRepository.save(user);

        LocalDateTime now = LocalDateTime.now();
        boolean membershipCreated = false;
        boolean membershipReactivated = false;
        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationId(user.getId(), actorContext.organizationId())
                .orElse(null);

        if (membership == null) {
            membership = new OrganizationUser();
            membership.setOrganization(actorContext.membership().getOrganization());
            membership.setUser(user);
            membership.setRole(UserRole.CUSTOMER);
            membership.setBaseBranch(actorContext.branch());
            membership.setLastSelectedOrganizationId(actorContext.organizationId());
            membership.setLastSelectedBranchId(actorContext.branch().getId());
            membership.setIsActive(true);
            membership.setCreatedAt(now);
            membership = organizationUserRepository.save(membership);
            membershipCreated = true;
        } else if (!Boolean.TRUE.equals(membership.getIsActive())) {
            membership.setIsActive(true);
            membership = organizationUserRepository.save(membership);
            membershipReactivated = true;
        }

        boolean branchAccessCreated = false;
        boolean branchAccessReactivated = false;
        UserBranchAccess branchAccess = userBranchAccessRepository
                .findByOrganizationUserIdAndBranchId(membership.getId(), actorContext.branch().getId())
                .orElse(null);

        if (branchAccess == null) {
            branchAccess = new UserBranchAccess();
            branchAccess.setOrganizationUser(membership);
            branchAccess.setBranch(actorContext.branch());
            branchAccess.setIsActive(true);
            branchAccess.setGrantedAt(now);
            branchAccess.setCreatedAt(now);
            userBranchAccessRepository.save(branchAccess);
            branchAccessCreated = true;
        } else if (!Boolean.TRUE.equals(branchAccess.getIsActive())) {
            branchAccess.setIsActive(true);
            if (branchAccess.getGrantedAt() == null) {
                branchAccess.setGrantedAt(now);
            }
            userBranchAccessRepository.save(branchAccess);
            branchAccessReactivated = true;
        }

        CreateCustomerResponseDto response = new CreateCustomerResponseDto();
        response.setMessage("Customer created successfully");
        response.setUserId(user.getId());
        response.setCustomerName(user.getName());
        response.setPhone(user.getPhone());
        response.setOrganizationId(actorContext.organizationId());
        response.setOrganizationName(actorContext.organizationName());
        response.setOrganizationUserId(membership.getId());
        response.setMembershipCreated(membershipCreated);
        response.setMembershipReactivated(membershipReactivated);
        response.setBaseBranchId(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getId());
        response.setBaseBranchName(membership.getBaseBranch() == null ? null : membership.getBaseBranch().getName());
        response.setBranchAccessCreated(branchAccessCreated);
        response.setBranchAccessReactivated(branchAccessReactivated);

        log.info(
                "Manual customer created in current context. actorEmail: {}, customerUserId: {}, organizationId: {}, branchId: {}",
                actorEmail,
                user.getId(),
                actorContext.organizationId(),
                actorContext.branch().getId());
        return response;
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

    private ManualCustomerContext resolveManualCustomerContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current organization and branch context are required");
        }

        UserRole actorRole = context.getCurrentRole() == null || context.getCurrentRole().isBlank()
                ? actor.getRole()
                : UserRole.valueOf(context.getCurrentRole());
        if (actorRole != UserRole.MANAGER && actorRole != UserRole.ADMIN && actorRole != UserRole.SUPER_ADMIN) {
            throw new SecurityException("You are not authorized to create customers");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Caller organization membership not found"));

        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(),
                        context.getCurrentOrganization().getId())
                .orElseThrow(() -> new java.util.NoSuchElementException("Current branch not found"));

        boolean branchAccessible = membership.getBaseBranch() != null
                && branch.getId().equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    branch.getId());
        }

        if (!branchAccessible) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new ManualCustomerContext(
                actor,
                membership,
                branch,
                context.getCurrentOrganization().getId(),
                context.getCurrentOrganization().getName());
    }

    private record ManualCustomerContext(
            User actor,
            OrganizationUser membership,
            Branch branch,
            Long organizationId,
            String organizationName) {
    }

}
