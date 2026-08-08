package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.GameActivityOptionDto;
import com.youngstersclub.app.dto.GameActivityOrderCreateRequest;
import com.youngstersclub.app.dto.GameActivityOrderResponseDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Game;
import com.youngstersclub.app.entity.GameActivityOrder;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.GameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class GameActivityServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameActivityOrderRepository gameActivityOrderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserDueService userDueService;

    @Mock
    private OrganizationContextService organizationContextService;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private OrganizationUserRepository organizationUserRepository;

    @Mock
    private UserBranchAccessRepository userBranchAccessRepository;

    @InjectMocks
    private GameActivityService gameActivityService;

    private User actor;
    private User parent;
    private Organization organization;
    private Branch branch;
    private OrganizationUser actorMembership;
    private OrganizationUser parentMembership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("manager@test.com");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        parent = new User();
        parent.setId(25);
        parent.setName("Rahul");
        parent.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters Sports Club");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        actorMembership = new OrganizationUser();
        actorMembership.setId(41L);
        actorMembership.setUser(actor);
        actorMembership.setOrganization(organization);
        actorMembership.setBaseBranch(branch);
        actorMembership.setRole(UserRole.MANAGER);
        actorMembership.setIsActive(true);
        actorMembership.setCreatedAt(LocalDateTime.now());

        parentMembership = new OrganizationUser();
        parentMembership.setId(42L);
        parentMembership.setUser(parent);
        parentMembership.setOrganization(organization);
        parentMembership.setBaseBranch(branch);
        parentMembership.setRole(UserRole.CUSTOMER);
        parentMembership.setIsActive(true);
        parentMembership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void searchActiveGamesUsesCurrentBranch() {
        Game game = buildGame(101L, "Soft Play Zone", BigDecimal.valueOf(3));
        mockAuthorizedContext();
        when(gameRepository.findTop10ByBranch_IdAndIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(
                branch.getId(),
                "soft"))
                .thenReturn(List.of(game));

        List<GameActivityOptionDto> results = gameActivityService.searchActiveGames("soft", "manager@test.com");

        assertEquals(1, results.size());
        assertEquals("Soft Play Zone", results.get(0).getGameName());
        verify(gameRepository)
                .findTop10ByBranch_IdAndIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(branch.getId(), "soft");
    }

    @Test
    void createOrdersAssignsCurrentBranchAndBranchGameRate() {
        mockAuthorizedContext();
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(parent.getId(), organization.getId()))
                .thenReturn(Optional.of(parentMembership));
        Game game = buildGame(101L, "Soft Play Zone", BigDecimal.valueOf(3));
        when(gameRepository.findByIdInAndBranch_IdAndIsActiveTrue(List.of(101L), branch.getId()))
                .thenReturn(List.of(game));
        when(gameActivityOrderRepository.save(any(GameActivityOrder.class))).thenAnswer(invocation -> {
            GameActivityOrder order = invocation.getArgument(0);
            order.setId(501L);
            return order;
        });

        GameActivityOrderCreateRequest.ActivityRequest activity = new GameActivityOrderCreateRequest.ActivityRequest();
        activity.setGameId(101L);
        activity.setDurationMinutes(15);
        activity.setNumberOfChildren(2);

        GameActivityOrderCreateRequest request = new GameActivityOrderCreateRequest();
        request.setParentUserId(parent.getId());
        request.setCreatedBy(actor.getId());
        request.setActivities(List.of(activity));

        GameActivityOrderResponseDto response = gameActivityService.createOrders(request, "manager@test.com");

        ArgumentCaptor<GameActivityOrder> captor = ArgumentCaptor.forClass(GameActivityOrder.class);
        verify(gameActivityOrderRepository).save(captor.capture());
        GameActivityOrder order = captor.getValue();

        assertSame(branch, order.getBranch());
        assertSame(actor, order.getCreatedBy());
        assertSame(parent, order.getParentUser());
        assertSame(game, order.getGame());
        assertEquals(0, BigDecimal.valueOf(90).compareTo(order.getTotalAmount()));
        assertEquals(1, response.getOrderCount());
        assertEquals(0, BigDecimal.valueOf(90).compareTo(response.getTotalAmount()));
        verify(userDueService).syncBranchDue(parent, branch);
    }

    @Test
    void createOrdersRejectsParentOutsideCurrentOrganization() {
        mockAuthorizedContext();
        when(userRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(parent.getId(), organization.getId()))
                .thenReturn(Optional.empty());

        GameActivityOrderCreateRequest.ActivityRequest activity = new GameActivityOrderCreateRequest.ActivityRequest();
        activity.setGameId(101L);
        activity.setDurationMinutes(15);
        activity.setNumberOfChildren(1);

        GameActivityOrderCreateRequest request = new GameActivityOrderCreateRequest();
        request.setParentUserId(parent.getId());
        request.setCreatedBy(actor.getId());
        request.setActivities(List.of(activity));

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> gameActivityService.createOrders(request, "manager@test.com"));

        assertEquals("Parent customer does not belong to the current organization", exception.getMessage());
    }

    @Test
    void branchSpecificPaidEarningsDelegatesToRepository() {
        when(gameActivityOrderRepository.getPaidEarningsBetweenAndBranchId(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 2, 0, 0),
                2L))
                .thenReturn(BigDecimal.valueOf(120));

        BigDecimal result = gameActivityService.getPaidEarningsBetween(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 2, 0, 0),
                2L);

        assertEquals(0, BigDecimal.valueOf(120).compareTo(result));
    }

    private void mockAuthorizedContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }

    private Game buildGame(Long id, String name, BigDecimal rate) {
        Game game = new Game();
        game.setId(id);
        game.setGameName(name);
        game.setBasePricePerMinute(rate);
        game.setBranch(branch);
        game.setIsActive(true);
        return game;
    }
}
