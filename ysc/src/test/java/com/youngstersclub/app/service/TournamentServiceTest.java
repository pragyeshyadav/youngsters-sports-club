package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.TournamentRegistrationResult;
import com.youngstersclub.app.dto.TournamentResponse;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.Tournament;
import com.youngstersclub.app.entity.TournamentRegistration;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.TournamentRegistrationRepository;
import com.youngstersclub.app.repository.TournamentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class TournamentServiceTest {

    @Mock
    private TournamentRepository tournamentRepository;

    @Mock
    private TournamentRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationUserRepository organizationUserRepository;

    @Mock
    private UserBranchAccessRepository userBranchAccessRepository;

    @Mock
    private BrevoEmailService brevoEmailService;

    @InjectMocks
    private TournamentService tournamentService;

    private User actor;
    private Organization organization;
    private Branch branch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(15);
        actor.setEmail("player@test.com");
        actor.setIsActive(true);
        actor.setRole(UserRole.CUSTOMER);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters");
        organization.setEmail("org@test.com");
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

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getActiveSummerOlympicsEventsUsesCurrentBranch() {
        mockAuthorizedContext();
        Tournament tournament = buildTournament(101L, "Snooker Singles");
        Tournament secondTournament = buildTournament(102L, "Table Tennis Singles");
        when(tournamentRepository.findByBranch_IdAndIsActiveTrueOrderByNameAsc(branch.getId()))
                .thenReturn(List.of(tournament, secondTournament));

        List<TournamentResponse> response = tournamentService.getActiveSummerOlympicsEvents("player@test.com");

        assertEquals(2, response.size());
        assertEquals("Snooker Singles", response.get(0).getName());
        assertEquals("Table Tennis Singles", response.get(1).getName());
        verify(tournamentRepository).findByBranch_IdAndIsActiveTrueOrderByNameAsc(branch.getId());
    }

    @Test
    void registerUserForTournamentsRegistersWithinCurrentBranch() {
        mockAuthorizedContext();
        TransactionSynchronizationManager.initSynchronization();
        actor.setName("Rahul Sharma");
        actor.setPhone("9876543210");
        Tournament tournament = buildTournament(101L, "Snooker Singles");
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(101L, branch.getId()))
                .thenReturn(Optional.of(tournament));
        when(registrationRepository.existsByTournamentIdAndUserId(101L, actor.getId())).thenReturn(false);

        TournamentRegistrationResult result =
                tournamentService.registerUserForTournaments(actor.getId(), List.of(101L), "player@test.com");

        assertEquals(List.of("Snooker Singles"), result.getSuccessfullyRegistered());
        ArgumentCaptor<TournamentRegistration> captor = ArgumentCaptor.forClass(TournamentRegistration.class);
        verify(registrationRepository).save(captor.capture());
        assertEquals(tournament, captor.getValue().getTournament());
        assertEquals(actor, captor.getValue().getUser());

        runAfterCommitCallbacks();
        verify(brevoEmailService).sendTournamentRegistrationNotification(
                eq("Rahul Sharma"),
                eq("9876543210"),
                eq(List.of("Snooker Singles")),
                eq(List.of()),
                eq("org@test.com"));
    }

    @Test
    void registerUserForTournamentsSendsSingleNotificationForMixedResults() {
        mockAuthorizedContext();
        TransactionSynchronizationManager.initSynchronization();
        actor.setName("Rahul Sharma");
        actor.setPhone("9876543210");

        Tournament snooker = buildTournament(101L, "Snooker Singles");
        Tournament chess = buildTournament(102L, "Chess Championship");

        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(101L, branch.getId()))
                .thenReturn(Optional.of(snooker));
        when(tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(102L, branch.getId()))
                .thenReturn(Optional.of(chess));
        when(registrationRepository.existsByTournamentIdAndUserId(101L, actor.getId())).thenReturn(false);
        when(registrationRepository.existsByTournamentIdAndUserId(102L, actor.getId())).thenReturn(true);

        TournamentRegistrationResult result =
                tournamentService.registerUserForTournaments(actor.getId(), List.of(101L, 102L), "player@test.com");

        assertEquals(List.of("Snooker Singles"), result.getSuccessfullyRegistered());
        assertEquals(List.of("Chess Championship"), result.getAlreadyRegistered());

        runAfterCommitCallbacks();
        verify(brevoEmailService).sendTournamentRegistrationNotification(
                eq("Rahul Sharma"),
                eq("9876543210"),
                eq(List.of("Snooker Singles")),
                eq(List.of("Chess Championship")),
                eq("org@test.com"));
    }

    @Test
    void registerUserForTournamentsSkipsNotificationWhenEverythingAlreadyRegistered() {
        mockAuthorizedContext();
        TransactionSynchronizationManager.initSynchronization();
        Tournament chess = buildTournament(102L, "Chess Championship");

        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(102L, branch.getId()))
                .thenReturn(Optional.of(chess));
        when(registrationRepository.existsByTournamentIdAndUserId(102L, actor.getId())).thenReturn(true);

        TournamentRegistrationResult result =
                tournamentService.registerUserForTournaments(actor.getId(), List.of(102L), "player@test.com");

        assertEquals(List.of(), result.getSuccessfullyRegistered());
        assertEquals(List.of("Chess Championship"), result.getAlreadyRegistered());
        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size());
        verify(brevoEmailService, never()).sendTournamentRegistrationNotification(any(), any(), any(), any(), any());
    }

    @Test
    void registerUserForTournamentsRejectsCrossBranchTournament() {
        mockAuthorizedContext();
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(tournamentRepository.findByIdAndBranch_IdAndIsActiveTrue(101L, branch.getId()))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tournamentService.registerUserForTournaments(actor.getId(), List.of(101L), "player@test.com"));

        assertEquals("Tournament not found", exception.getMessage());
    }

    @Test
    void registerUserForTournamentsRejectsMismatchedAuthenticatedUser() {
        mockAuthorizedContext();

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> tournamentService.registerUserForTournaments(999, List.of(101L), "player@test.com"));

        assertEquals("Authenticated user does not match the registration request", exception.getMessage());
    }

    private void mockAuthorizedContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.CUSTOMER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("player@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("player@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }

    private Tournament buildTournament(Long id, String name) {
        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName(name);
        tournament.setEventName("Vindhya Olympics 2K26");
        tournament.setRegistrationFee(BigDecimal.valueOf(100));
        tournament.setStartDate(LocalDate.of(2026, 8, 10));
        tournament.setEndDate(LocalDate.of(2026, 8, 12));
        tournament.setBranch(branch);
        tournament.setIsActive(true);
        return tournament;
    }

    private void runAfterCommitCallbacks() {
        List<TransactionSynchronization> synchronizations =
                List.copyOf(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
    }
}
