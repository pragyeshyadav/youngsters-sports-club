package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.dto.PlayerSummaryDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PlayerSummaryServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;
    @Mock private PendingDueService pendingDueService;

    @InjectMocks private PlayerSummaryService playerSummaryService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("manager@test.com");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters Sports Club & Kids Ocean Dreamland");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(30L);
        membership.setOrganization(organization);
        membership.setUser(actor);
        membership.setRole(UserRole.MANAGER);
        membership.setBaseBranch(branch);
        membership.setIsActive(true);
        membership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void getPlayerSummariesReturnsCurrentBranchPlayersAndUsesPendingDueService() {
        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(buildContext());
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
        when(userRepository.getPlayerSummaryBasesForBranch(organization.getId(), branch.getId())).thenReturn(List.of(
                projection(101, "Rahul", "rahul@test.com", 12L),
                projection(102, "Aman", "aman@test.com", 5L)));
        Map<Long, CustomerBranchDue> branchDues = new LinkedHashMap<>();
        branchDues.put(101L, new CustomerBranchDue(
                101L,
                branch.getId(),
                BigDecimal.valueOf(200),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(350)));
        branchDues.put(102L, new CustomerBranchDue(
                102L,
                branch.getId(),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(25),
                BigDecimal.valueOf(200)));
        when(pendingDueService.calculateCustomerDues(List.of(101L, 102L), branch.getId())).thenReturn(branchDues);

        Page<PlayerSummaryDto> result = playerSummaryService.getPlayerSummaries(PageRequest.of(0, 20), "manager@test.com");

        assertEquals(2, result.getTotalElements());
        assertEquals("Rahul", result.getContent().get(0).getName());
        assertEquals(12L, result.getContent().get(0).getFramesPlayed());
        assertEquals(0, BigDecimal.valueOf(350).compareTo(result.getContent().get(0).getTotalDue()));
        verify(pendingDueService).calculateCustomerDues(List.of(101L, 102L), branch.getId());
    }

    @Test
    void getPlayerSummariesRejectsMissingActorEmail() {
        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> playerSummaryService.getPlayerSummaries(PageRequest.of(0, 20), null));

        assertEquals("Authenticated user email is required", exception.getMessage());
    }

    private OrganizationContextDto buildContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);
        return context;
    }

    private PlayerSummaryBaseProjection projection(Integer userId, String name, String email, Long framesPlayed) {
        return new PlayerSummaryBaseProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getEmail() {
                return email;
            }

            @Override
            public Long getFramesPlayed() {
                return framesPlayed;
            }
        };
    }
}
