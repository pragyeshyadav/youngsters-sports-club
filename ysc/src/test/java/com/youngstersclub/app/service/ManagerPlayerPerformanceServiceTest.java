package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.PlayerPerformanceResponseDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ManagerPlayerPerformanceServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private OrganizationContextService contextService;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private UserBranchAccessRepository branchAccessRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private PlayerPerformanceService performanceService;

    private ManagerPlayerPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new ManagerPlayerPerformanceService(
                userRepository, userService, contextService, organizationUserRepository,
                branchAccessRepository, branchRepository, performanceService);
    }

    @Test
    void selectedPlayerMustBelongToCurrentOrganizationAndBranch() {
        User actor = user(1, "manager@example.com", true);
        User player = user(14, "player@example.com", true);
        OrganizationUser actorMembership = membership(10L, actor, null);
        OrganizationUser playerMembership = membership(20L, player, null);
        OrganizationContextDto context = context(UserRole.MANAGER);
        Branch branch = new Branch();
        branch.setId(101L);

        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(actor));
        when(contextService.resolveContext("manager@example.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(1, 7L))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(101L, 7L))
                .thenReturn(Optional.of(branch));
        when(branchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(10L, 101L))
                .thenReturn(true);
        when(userRepository.findById(14)).thenReturn(Optional.of(player));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(14, 7L))
                .thenReturn(Optional.of(playerMembership));
        when(branchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(20L, 101L))
                .thenReturn(false);

        assertThrows(SecurityException.class, () -> service.getPlayerPerformance("manager@example.com", 14));
        verify(performanceService, never()).getForUser(player);
    }

    @Test
    void authorizedManagerDelegatesPerformanceToSharedService() {
        User actor = user(1, "manager@example.com", true);
        User player = user(14, "player@example.com", true);
        OrganizationUser actorMembership = membership(10L, actor, 101L);
        OrganizationUser playerMembership = membership(20L, player, 101L);
        OrganizationContextDto context = context(UserRole.ADMIN);
        Branch branch = new Branch();
        branch.setId(101L);
        PlayerPerformanceResponseDto expected = new PlayerPerformanceResponseDto(null, null, null);

        when(userRepository.findByEmail("manager@example.com")).thenReturn(Optional.of(actor));
        when(contextService.resolveContext("manager@example.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(1, 7L))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(101L, 7L))
                .thenReturn(Optional.of(branch));
        when(userRepository.findById(14)).thenReturn(Optional.of(player));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(14, 7L))
                .thenReturn(Optional.of(playerMembership));
        when(performanceService.getForUser(player)).thenReturn(expected);

        service.getPlayerPerformance("manager@example.com", 14);

        verify(performanceService).getForUser(player);
    }

    private OrganizationContextDto context(UserRole role) {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(role.name());
        context.setCurrentOrganization(new OrganizationOptionDto(7L, "YSC"));
        context.setCurrentBranch(new BranchOptionDto(101L, "Satna"));
        return context;
    }

    private User user(int id, String email, boolean active) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setIsActive(active);
        return user;
    }

    private OrganizationUser membership(long id, User user, Long baseBranchId) {
        OrganizationUser membership = new OrganizationUser();
        membership.setId(id);
        membership.setUser(user);
        if (baseBranchId != null) {
            Branch branch = new Branch();
            branch.setId(baseBranchId);
            membership.setBaseBranch(branch);
        }
        return membership;
    }
}
