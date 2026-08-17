package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TournamentResponse;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.Tournament;
import com.youngstersclub.app.entity.TournamentRegistration;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.TournamentRegistrationRepository;
import com.youngstersclub.app.repository.TournamentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import com.youngstersclub.app.dto.TournamentRegistrationResult;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public TournamentService(TournamentRepository tournamentRepository,
                             TournamentRegistrationRepository registrationRepository,
                             UserRepository userRepository,
                             OrganizationContextService organizationContextService,
                             BranchRepository branchRepository,
                             OrganizationUserRepository organizationUserRepository,
                             UserBranchAccessRepository userBranchAccessRepository) {
        this.tournamentRepository = tournamentRepository;
        this.registrationRepository = registrationRepository;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    public List<TournamentResponse> getActiveSummerOlympicsEvents(String actorEmail) {
        TournamentBranchContext context = resolveTournamentContext(actorEmail);
        return tournamentRepository.findByBranch_IdAndIsActiveTrueOrderByNameAsc(context.branch().getId())
                .stream()
                .map(t -> new TournamentResponse(
                        t.getId(),
                        t.getName(),
                        t.getEventName(),
                        t.getRegistrationFee()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public TournamentRegistrationResult registerUserForTournaments(
            Integer userId,
            List<Long> tournamentIds,
            String actorEmail) {
        TournamentBranchContext context = resolveTournamentContext(actorEmail);
        if (!context.actor().getId().equals(userId)) {
            throw new SecurityException("Authenticated user does not match the registration request");
        }

        User user = userRepository.findById(userId)
                .filter(foundUser -> Boolean.TRUE.equals(foundUser.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        validateTournamentMembership(user.getId(), context.organizationId());

        TournamentRegistrationResult result = new TournamentRegistrationResult();

        for (Long tournamentId : tournamentIds) {
            Tournament tournament = tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(
                            tournamentId,
                            context.branch().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tournament not found"));

            boolean isAlreadyRegistered = registrationRepository.existsByTournamentIdAndUserId(tournamentId, userId);

            if (isAlreadyRegistered) {
                result.getAlreadyRegistered().add(tournament.getName());
            } else {
                TournamentRegistration registration = new TournamentRegistration();
                registration.setTournament(tournament);
                registration.setUser(user);
                registrationRepository.save(registration);
                
                result.getSuccessfullyRegistered().add(tournament.getName());
            }
        }
        return result;
    }

    private void validateTournamentMembership(Integer userId, Long organizationId) {
        if (organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(userId, organizationId).isEmpty()) {
            throw new SecurityException("User does not belong to the current organization");
        }
    }

    private TournamentBranchContext resolveTournamentContext(String actorEmail) {
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

        boolean hasBranchAccess = branch.equals(membership.getBaseBranch())
                || userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                        membership.getId(),
                        branchId);
        if (!hasBranchAccess) {
            throw new SecurityException("User does not have access to the current branch");
        }

        return new TournamentBranchContext(actor, organizationId, branch);
    }

    private record TournamentBranchContext(User actor, Long organizationId, Branch branch) {}
}
