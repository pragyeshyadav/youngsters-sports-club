package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.KidsSessionEndRequest;
import com.youngstersclub.app.dto.KidsSessionResponseDto;
import com.youngstersclub.app.dto.KidsSessionStartRequest;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Child;
import com.youngstersclub.app.entity.KidsPlaySession;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
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
class KidsPlayServiceTest {

    @Mock
    private KidsPlaySessionRepository kidsPlaySessionRepository;

    @Mock
    private SnookerTableRepository snookerTableRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChildService childService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private GameActivityService gameActivityService;

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
    private KidsPlayService kidsPlayService;

    private User actor;
    private User parent;
    private Child child;
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

        child = new Child();
        child.setId(55L);
        child.setName("Aarav");
        child.setParentUser(parent);

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
    void startSessionAssignsCurrentBranchAndBranchSpecificRate() {
        mockAuthorizedContext();
        when(kidsPlaySessionRepository.findActiveByChildId(55L)).thenReturn(Optional.empty());
        when(userRepository.findById(25)).thenReturn(Optional.of(parent));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(parent.getId(), organization.getId()))
                .thenReturn(Optional.of(parentMembership));
        when(childService.getOwnedChild(55L, 25)).thenReturn(child);

        SnookerTable kidsTable = new SnookerTable();
        kidsTable.setId(99L);
        kidsTable.setTableName("Kids Ocean Dream Land");
        kidsTable.setRatePerMinute(BigDecimal.valueOf(3));
        kidsTable.setBranch(branch);
        kidsTable.setIsActive(true);
        when(snookerTableRepository.findByBranch_IdAndTableNameIgnoreCase(branch.getId(), "Kids Ocean Dream Land"))
                .thenReturn(Optional.of(kidsTable));
        when(kidsPlaySessionRepository.save(any(KidsPlaySession.class))).thenAnswer(invocation -> {
            KidsPlaySession session = invocation.getArgument(0);
            session.setId(501L);
            return session;
        });

        KidsSessionStartRequest request = new KidsSessionStartRequest();
        request.setParentUserId(25);
        request.setChildId(55L);

        KidsSessionResponseDto response = kidsPlayService.startSession(request, "manager@test.com");

        ArgumentCaptor<KidsPlaySession> captor = ArgumentCaptor.forClass(KidsPlaySession.class);
        verify(kidsPlaySessionRepository).save(captor.capture());
        KidsPlaySession saved = captor.getValue();
        assertSame(branch, saved.getBranch());
        assertEquals(0, BigDecimal.valueOf(3).compareTo(saved.getRatePerMinute()));
        assertEquals(501L, response.getSessionId());
        verify(userDueService).syncBranchDue(parent, branch);
    }

    @Test
    void endSessionLoadsBySessionAndCurrentBranchOnly() {
        mockAuthorizedContext();
        KidsPlaySession session = new KidsPlaySession();
        session.setId(601L);
        session.setParentUser(parent);
        session.setChild(child);
        session.setBranch(branch);
        session.setStartTime(LocalDateTime.now().minusMinutes(10));
        session.setRatePerMinute(BigDecimal.valueOf(2));
        session.setStatus("STARTED");
        session.setPaymentStatus("UNPAID");

        when(kidsPlaySessionRepository.findByIdAndBranch_Id(601L, branch.getId())).thenReturn(Optional.of(session));
        when(kidsPlaySessionRepository.save(any(KidsPlaySession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        KidsSessionEndRequest request = new KidsSessionEndRequest();
        request.setSessionId(601L);
        request.setParentUserId(25);

        KidsSessionResponseDto response = kidsPlayService.endSession(request, "manager@test.com");

        assertEquals("ENDED", response.getStatus());
        assertSame(branch, session.getBranch());
        verify(kidsPlaySessionRepository).findByIdAndBranch_Id(601L, branch.getId());
    }

    @Test
    void getAllActiveSessionsUsesCurrentBranchOnly() {
        mockAuthorizedContext();
        KidsPlaySession session = new KidsPlaySession();
        session.setId(701L);
        session.setParentUser(parent);
        session.setChild(child);
        session.setBranch(branch);
        session.setStartTime(LocalDateTime.now().minusMinutes(5));

        when(kidsPlaySessionRepository.findAllActiveSessionsByBranchId(branch.getId())).thenReturn(List.of(session));

        List<KidsSessionResponseDto> result = kidsPlayService.getAllActiveSessions("manager@test.com");

        assertEquals(1, result.size());
        assertEquals(701L, result.get(0).getSessionId());
        verify(kidsPlaySessionRepository).findAllActiveSessionsByBranchId(branch.getId());
    }

    @Test
    void startSessionRejectsParentOutsideCurrentOrganization() {
        mockAuthorizedContext();
        when(kidsPlaySessionRepository.findActiveByChildId(55L)).thenReturn(Optional.empty());
        when(userRepository.findById(25)).thenReturn(Optional.of(parent));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(parent.getId(), organization.getId()))
                .thenReturn(Optional.empty());

        KidsSessionStartRequest request = new KidsSessionStartRequest();
        request.setParentUserId(25);
        request.setChildId(55L);

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> kidsPlayService.startSession(request, "manager@test.com"));

        assertEquals("Parent does not belong to the current organization", exception.getMessage());
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
}
