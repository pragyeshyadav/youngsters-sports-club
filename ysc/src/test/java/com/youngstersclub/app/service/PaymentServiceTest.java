package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.PaymentRequest;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FramePlayerRepository;
import com.youngstersclub.app.repository.FrameRepository;
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
class PaymentServiceTest {

    @Mock private FrameRepository frameRepository;
    @Mock private ConsumableOrderRepository consumableOrderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private BranchRepository branchRepository;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;
    @Mock private ConsumableService consumableService;
    @Mock private KidsPlayService kidsPlayService;
    @Mock private FramePlayerRepository framePlayerRepository;
    @Mock private UserPaymentSummaryService userPaymentSummaryService;
    @Mock private WhatsAppService whatsAppService;
    @Mock private UserDueService userDueService;

    @InjectMocks private PaymentService paymentService;

    private User actor;
    private User customer;
    private Organization organization;
    private Branch branch;
    private Branch otherBranch;
    private OrganizationUser membership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(14);
        actor.setEmail("manager@test.com");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        customer = new User();
        customer.setId(25);
        customer.setName("Rahul");
        customer.setPhone("9876543210");
        customer.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters Sports Club");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        otherBranch = new Branch();
        otherBranch.setId(3L);
        otherBranch.setName("Rewa");
        otherBranch.setOrganization(organization);
        otherBranch.setIsActive(true);

        membership = new OrganizationUser();
        membership.setId(20L);
        membership.setUser(actor);
        membership.setOrganization(organization);
        membership.setBaseBranch(branch);
        membership.setRole(UserRole.MANAGER);
        membership.setIsActive(true);
        membership.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void settlePaymentUsesOnlyCurrentBranchDueAndPersistsPaymentWithBranch() {
        mockAuthorizedContext();
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));

        Frame frame = new Frame();
        frame.setId(501);
        frame.setBranch(branch);
        frame.setPaymentDue(BigDecimal.valueOf(100));
        frame.setPaymentStatus(PaymentStatus.UNPAID);
        frame.setStartTime(LocalDateTime.now().minusMinutes(30));

        when(frameRepository.findDueFramesByUserAndBranchOrderByStartTime(customer.getId(), branch.getId()))
                .thenReturn(List.of(frame));
        when(consumableService.getUnpaidOrders(customer.getId(), branch.getId())).thenReturn(List.of());
        when(userPaymentSummaryService.getBranchPaymentSummary(customer.getId(), branch.getId()))
                .thenReturn(
                        new UserPaymentSummaryDto(BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO),
                        new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(frameRepository.save(any(Frame.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentRequest request = new PaymentRequest();
        request.setUserId(customer.getId());
        request.setAmount(BigDecimal.valueOf(100));
        request.setDiscount(BigDecimal.ZERO);
        request.setMode(PaymentMethod.CASH.name());

        paymentService.settlePayment(request, "manager@test.com");

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        Payment payment = paymentCaptor.getValue();
        assertSame(branch, payment.getBranch());
        assertSame(customer, payment.getUser());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(payment.getAmount()));
        verify(frameRepository).findDueFramesByUserAndBranchOrderByStartTime(customer.getId(), branch.getId());
        verify(consumableService).getUnpaidOrders(customer.getId(), branch.getId());
        verify(userDueService).syncBranchDue(customer, branch);
        verify(userDueService, never()).syncBranchDue(customer, otherBranch);
        verify(whatsAppService).sendPaymentSettlementMessage(
                eq(customer),
                eq(BigDecimal.valueOf(100)),
                eq(BigDecimal.ZERO),
                eq(BigDecimal.ZERO),
                eq(organization.getId()),
                eq("Youngsters Sports Club"),
                eq(null),
                eq(branch.getId()),
                eq(branch.getName()));
    }

    @Test
    void settlePaymentRejectsUnauthorizedBranchContext() {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(organization.getId(), organization.getName()));
        context.setCurrentBranch(new BranchOptionDto(branch.getId(), branch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(membership));
        membership.setBaseBranch(null);
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
        when(userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId()))
                .thenReturn(false);

        PaymentRequest request = new PaymentRequest();
        request.setUserId(customer.getId());
        request.setAmount(BigDecimal.TEN);
        request.setDiscount(BigDecimal.ZERO);
        request.setMode(PaymentMethod.CASH.name());

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> paymentService.settlePayment(request, "manager@test.com"));

        assertEquals("You do not have access to the current branch", exception.getMessage());
    }

    @Test
    void settlePaymentUsesOrganizationOwnedByCurrentBranchForWhatsappTemplate() {
        Organization cueSociety = new Organization();
        cueSociety.setId(9L);
        cueSociety.setName("The Cue Society");
        cueSociety.setPhone("2222222222");
        cueSociety.setIsActive(true);

        Branch cueBranch = new Branch();
        cueBranch.setId(12L);
        cueBranch.setName("Cue Main");
        cueBranch.setOrganization(cueSociety);
        cueBranch.setIsActive(true);

        membership.setOrganization(cueSociety);
        membership.setBaseBranch(cueBranch);

        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(UserRole.MANAGER.name());
        context.setCurrentOrganization(new OrganizationOptionDto(cueSociety.getId(), cueSociety.getName()));
        context.setCurrentBranch(new BranchOptionDto(cueBranch.getId(), cueBranch.getName()));
        context.setHasPersistedContext(true);
        context.setRequiresSelection(false);

        when(userRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(actor));
        when(organizationContextService.resolveContext("manager@test.com")).thenReturn(context);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), cueSociety.getId()))
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(cueBranch.getId(), cueSociety.getId()))
                .thenReturn(Optional.of(cueBranch));
        when(userRepository.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(frameRepository.findDueFramesByUserAndBranchOrderByStartTime(customer.getId(), cueBranch.getId()))
                .thenReturn(List.of());
        when(consumableService.getUnpaidOrders(customer.getId(), cueBranch.getId())).thenReturn(List.of());
        when(userPaymentSummaryService.getBranchPaymentSummary(customer.getId(), cueBranch.getId()))
                .thenReturn(
                        new UserPaymentSummaryDto(BigDecimal.valueOf(110), BigDecimal.ZERO, BigDecimal.ZERO),
                        new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        PaymentRequest request = new PaymentRequest();
        request.setUserId(customer.getId());
        request.setAmount(BigDecimal.valueOf(100));
        request.setDiscount(BigDecimal.TEN);
        request.setMode(PaymentMethod.CASH.name());

        paymentService.settlePayment(request, "manager@test.com");

        verify(whatsAppService).sendPaymentSettlementMessage(
                eq(customer),
                eq(BigDecimal.valueOf(100)),
                eq(BigDecimal.TEN),
                eq(BigDecimal.ZERO),
                eq(cueSociety.getId()),
                eq("The Cue Society"),
                eq("2222222222"),
                eq(cueBranch.getId()),
                eq(cueBranch.getName()));
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
                .thenReturn(Optional.of(membership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }
}
