package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PlayerSummaryDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PlayerSummaryQueryRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PlayerSummaryService {

    private static final Logger log = LoggerFactory.getLogger(PlayerSummaryService.class);

    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final PlayerSummaryQueryRepository playerSummaryQueryRepository;

    public PlayerSummaryService(
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            PlayerSummaryQueryRepository playerSummaryQueryRepository) {
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.playerSummaryQueryRepository = playerSummaryQueryRepository;
    }

    public Page<PlayerSummaryDto> getPlayerSummaries(Pageable pageable, String actorEmail) {
        PlayerSummaryContext context = resolvePlayerSummaryContext(actorEmail);
        List<PlayerSummaryDto> pageRows = playerSummaryQueryRepository.findPlayerSummariesForBranch(
                context.organizationId(),
                context.branch().getId(),
                pageable.getPageSize(),
                pageable.getOffset());
        long total = playerSummaryQueryRepository.countPlayerSummariesForBranch(
                context.organizationId(),
                context.branch().getId());
        log.info(
                "action=SHOW_ALL_PLAYERS organizationId={} branchId={} actorUserId={} resultCount={}",
                context.organizationId(),
                context.branch().getId(),
                context.actor().getId(),
                total);
        return new PageImpl<>(pageRows, pageable, total);
    }

    private PlayerSummaryContext resolvePlayerSummaryContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new SecurityException("Current organization and branch context are required");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new NoSuchElementException("Caller organization membership not found"));

        Branch branch = branchRepository
                .findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(),
                        context.getCurrentOrganization().getId())
                .orElseThrow(() -> new NoSuchElementException("Current branch not found"));

        boolean branchAccessible = membership.getBaseBranch() != null
                && branch.getId().equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository
                    .existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId());
        }

        if (!branchAccessible) {
            throw new SecurityException("You do not have access to the current branch");
        }

        UserRole actorRole = context.getCurrentRole() == null || context.getCurrentRole().isBlank()
                ? actor.getRole()
                : UserRole.valueOf(context.getCurrentRole());

        return new PlayerSummaryContext(actor, branch, context.getCurrentOrganization().getId(), actorRole);
    }

    private record PlayerSummaryContext(
            User actor,
            Branch branch,
            Long organizationId,
            UserRole role) {
    }
}
