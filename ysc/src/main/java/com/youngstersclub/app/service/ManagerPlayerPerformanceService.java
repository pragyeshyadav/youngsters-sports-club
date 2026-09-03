package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PlayerPerformanceResponseDto;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.entity.UserBranchAccess;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManagerPlayerPerformanceService {
    private static final List<UserRole> STAFF_ROLES =
            List.of(UserRole.MANAGER, UserRole.ADMIN, UserRole.SUPER_ADMIN);

    private final UserRepository userRepository;
    private final UserService userService;
    private final OrganizationContextService organizationContextService;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final BranchRepository branchRepository;
    private final PlayerPerformanceService playerPerformanceService;

    public ManagerPlayerPerformanceService(
            UserRepository userRepository,
            UserService userService,
            OrganizationContextService organizationContextService,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            BranchRepository branchRepository,
            PlayerPerformanceService playerPerformanceService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.organizationContextService = organizationContextService;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.branchRepository = branchRepository;
        this.playerPerformanceService = playerPerformanceService;
    }

    @Transactional(readOnly = true)
    public List<UserSearchResultDto> searchPlayers(String actorEmail, String query) {
        authorizeCurrentStaff(actorEmail);
        return userService.searchUsersForCurrentBranch(query, actorEmail);
    }

    @Transactional(readOnly = true)
    public PlayerPerformanceResponseDto getPlayerPerformance(String actorEmail, Integer playerId) {
        Context context = authorizeCurrentStaff(actorEmail);
        User player = userRepository.findById(playerId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Player is not available in the current branch"));

        OrganizationUser playerMembership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(player.getId(), context.organizationId())
                .orElseThrow(() -> new SecurityException("Player is not available in the current branch"));
        if (!hasBranchAccess(playerMembership, context.branchId())) {
            throw new SecurityException("Player is not available in the current branch");
        }

        return playerPerformanceService.getForUser(player);
    }

    protected Context authorizeCurrentStaff(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));
        var context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new SecurityException("Current organization and branch context are required");
        }
        UserRole role = context.getCurrentRole() == null || context.getCurrentRole().isBlank()
                ? actor.getRole()
                : UserRole.valueOf(context.getCurrentRole());
        if (!STAFF_ROLES.contains(role)) {
            throw new SecurityException("You are not authorized to view player performance");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(
                        actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new SecurityException("Caller organization membership not found"));
        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new SecurityException("Current branch not found"));
        if (!hasBranchAccess(membership, branch.getId())) {
            throw new SecurityException("You do not have access to the current branch");
        }
        return new Context(context.getCurrentOrganization().getId(), branch.getId());
    }

    protected boolean hasBranchAccess(OrganizationUser membership, Long branchId) {
        if (membership.getBaseBranch() != null && branchId.equals(membership.getBaseBranch().getId())) {
            return true;
        }
        return userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                membership.getId(), branchId);
    }

    protected record Context(Long organizationId, Long branchId) {}
}
