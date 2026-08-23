package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.CustomerFeedback;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.CustomerFeedbackRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final CustomerFeedbackRepository customerFeedbackRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public FeedbackService(
            CustomerFeedbackRepository customerFeedbackRepository,
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository) {
        this.customerFeedbackRepository = customerFeedbackRepository;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    @Transactional
    public CustomerFeedback saveFeedback(CustomerFeedback request, String actorEmail) {
        validateRequest(request);

        FeedbackBranchContext context = resolveFeedbackContext(actorEmail);
        if (!context.actor().getId().equals(request.getUserId())) {
            throw new SecurityException("Authenticated user does not match the feedback request");
        }
        validateMembership(request.getUserId(), context.organizationId());

        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setUserId(request.getUserId());
        feedback.setBranch(context.branch());
        feedback.setStarRating(request.getStarRating());
        feedback.setFeedback(request.getFeedback().trim());
        return customerFeedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public List<CustomerFeedback> getCurrentBranchFeedback(String actorEmail) {
        FeedbackBranchContext context = resolveFeedbackContext(actorEmail);
        return customerFeedbackRepository.findByBranch_IdOrderByCreatedAtDesc(context.branch().getId());
    }

    private void validateRequest(CustomerFeedback request) {
        if (request == null
                || request.getUserId() == null
                || request.getStarRating() == null
                || request.getStarRating() < 1
                || request.getStarRating() > 5
                || request.getFeedback() == null
                || request.getFeedback().trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid feedback");
        }
    }

    private void validateMembership(Integer userId, Long organizationId) {
        if (organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(userId, organizationId).isEmpty()) {
            throw new SecurityException("User does not belong to the current organization");
        }
    }

    private FeedbackBranchContext resolveFeedbackContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new SecurityException("Current organization and branch context is required");
        }

        Long organizationId = context.getCurrentOrganization().getId();
        Long branchId = context.getCurrentBranch().getId();
        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organizationId)
                .orElseThrow(() -> new SecurityException("Organization membership not found"));
        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branchId, organizationId)
                .orElseThrow(() -> new SecurityException("Current branch is not accessible"));

        boolean hasBranchAccess = membership.getBaseBranch() != null
                && branchId.equals(membership.getBaseBranch().getId());
        if (!hasBranchAccess) {
            hasBranchAccess = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    branchId);
        }
        if (!hasBranchAccess) {
            throw new SecurityException("User does not have access to the current branch");
        }

        return new FeedbackBranchContext(actor, organizationId, branch);
    }

    private record FeedbackBranchContext(User actor, Long organizationId, Branch branch) {}
}
