package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.CustomerFeedback;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.CustomerFeedbackRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private CustomerFeedbackRepository customerFeedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrganizationUserRepository organizationUserRepository;

    @Mock
    private UserBranchAccessRepository userBranchAccessRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(15);
        actor.setEmail("customer@test.com");
        actor.setIsActive(true);
        actor.setRole(UserRole.CUSTOMER);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(11L);
        membership.setUser(actor);
        membership.setOrganization(organization);
        membership.setBaseBranch(branch);
        membership.setRole(UserRole.CUSTOMER);
        membership.setIsActive(true);
    }

    @Test
    void saveFeedbackAssignsCurrentBranchAndTrimsFeedback() {
        mockAuthorizedContext();
        when(customerFeedbackRepository.save(any(CustomerFeedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerFeedback request = new CustomerFeedback();
        request.setUserId(actor.getId());
        request.setStarRating(5);
        request.setFeedback(" Great place!  ");

        CustomerFeedback saved = feedbackService.saveFeedback(request, "customer@test.com");

        ArgumentCaptor<CustomerFeedback> captor = ArgumentCaptor.forClass(CustomerFeedback.class);
        verify(customerFeedbackRepository).save(captor.capture());
        CustomerFeedback persisted = captor.getValue();
        assertSame(branch, persisted.getBranch());
        assertEquals("Great place!", persisted.getFeedback());
        assertEquals(5, persisted.getStarRating());
        assertEquals(actor.getId(), persisted.getUserId());
        assertSame(persisted, saved);
    }

    @Test
    void saveFeedbackRejectsMismatchedAuthenticatedUser() {
        mockAuthorizedContext();

        CustomerFeedback request = new CustomerFeedback();
        request.setUserId(999);
        request.setStarRating(5);
        request.setFeedback("Great");

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> feedbackService.saveFeedback(request, "customer@test.com"));

        assertEquals("Authenticated user does not match the feedback request", exception.getMessage());
    }

    @Test
    void getCurrentBranchFeedbackReturnsOnlyCurrentBranchRecords() {
        mockAuthorizedContext();
        CustomerFeedback feedback = new CustomerFeedback();
        feedback.setId(100L);
        feedback.setUserId(actor.getId());
        feedback.setBranch(branch);
        feedback.setFeedback("Nice");
        feedback.setStarRating(4);
        when(customerFeedbackRepository.findByBranch_IdOrderByCreatedAtDesc(branch.getId()))
                .thenReturn(List.of(feedback));

        List<CustomerFeedback> results = feedbackService.getCurrentBranchFeedback("customer@test.com");

        assertEquals(1, results.size());
        assertSame(feedback, results.get(0));
        verify(customerFeedbackRepository).findByBranch_IdOrderByCreatedAtDesc(branch.getId());
    }

    private void mockAuthorizedContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.CUSTOMER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("customer@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }
}
