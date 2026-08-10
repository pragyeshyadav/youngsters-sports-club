package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchExpenseCreateRequest;
import com.youngstersclub.app.dto.BranchExpenseDto;
import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.BranchExpense;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.ExpenseType;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchExpenseRepository;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class BranchExpenseServiceTest {

    @Mock private BranchExpenseRepository branchExpenseRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationContextService organizationContextService;
    @Mock private OrganizationUserRepository organizationUserRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private UserBranchAccessRepository userBranchAccessRepository;
    @Mock private OrganizationRepository organizationRepository;

    @InjectMocks private BranchExpenseService branchExpenseService;

    private User actor;
    private User paidBy;
    private Organization organization;
    private Branch branch;
    private OrganizationUser actorMembership;
    private OrganizationUser paidByMembership;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(2);
        actor.setEmail("manager@test.com");
        actor.setName("Pragyesh");
        actor.setRole(UserRole.MANAGER);
        actor.setIsActive(true);

        paidBy = new User();
        paidBy.setId(12);
        paidBy.setEmail("prince@test.com");
        paidBy.setName("Prince");
        paidBy.setRole(UserRole.MANAGER);
        paidBy.setIsActive(true);

        organization = new Organization();
        organization.setId(1L);
        organization.setName("Youngsters");
        organization.setIsActive(true);

        branch = new Branch();
        branch.setId(2L);
        branch.setName("Satna");
        branch.setOrganization(organization);
        branch.setIsActive(true);

        actorMembership = new OrganizationUser();
        actorMembership.setId(30L);
        actorMembership.setUser(actor);
        actorMembership.setOrganization(organization);
        actorMembership.setBaseBranch(branch);
        actorMembership.setRole(UserRole.MANAGER);
        actorMembership.setIsActive(true);

        paidByMembership = new OrganizationUser();
        paidByMembership.setId(31L);
        paidByMembership.setUser(paidBy);
        paidByMembership.setOrganization(organization);
        paidByMembership.setBaseBranch(branch);
        paidByMembership.setRole(UserRole.MANAGER);
        paidByMembership.setIsActive(true);
    }

    @Test
    void createExpenseAssignsCurrentBranchOrganizationAndCreator() {
        mockAuthorizedContext();
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(paidBy.getId(), organization.getId()))
                .thenReturn(Optional.of(paidByMembership));
        when(branchExpenseRepository.save(any(BranchExpense.class))).thenAnswer(invocation -> {
            BranchExpense saved = invocation.getArgument(0);
            saved.setId(101L);
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        BranchExpenseCreateRequest request = new BranchExpenseCreateRequest();
        request.setExpenseName(" Electricity Bill ");
        request.setAmount(new BigDecimal("6500"));
        request.setExpenseType(ExpenseType.COUNTER_CASH);
        request.setPaidByUserId(paidBy.getId());
        request.setExpenseDate(LocalDate.now().minusDays(1));
        request.setNotes(" July bill ");

        BranchExpenseDto response = branchExpenseService.createExpense(request, actor.getEmail());

        ArgumentCaptor<BranchExpense> captor = ArgumentCaptor.forClass(BranchExpense.class);
        verify(branchExpenseRepository).save(captor.capture());
        BranchExpense persisted = captor.getValue();
        assertSame(organization, persisted.getOrganization());
        assertSame(branch, persisted.getBranch());
        assertSame(actor, persisted.getCreatedBy());
        assertSame(paidBy, persisted.getPaidBy());
        assertEquals("Electricity Bill", persisted.getExpenseName());
        assertEquals("July bill", persisted.getNotes());
        assertEquals(0, new BigDecimal("6500.00").compareTo(persisted.getAmount()));
        assertEquals(ExpenseType.COUNTER_CASH, response.getExpenseType());
        assertEquals("Prince", response.getPaidByName());
        assertEquals("Pragyesh", response.getCreatedByName());
    }

    @Test
    void createExpenseRejectsCustomerAsPaidBy() {
        mockAuthorizedContext();
        paidByMembership.setRole(UserRole.CUSTOMER);
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(paidBy.getId(), organization.getId()))
                .thenReturn(Optional.of(paidByMembership));

        BranchExpenseCreateRequest request = buildValidRequest();

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> branchExpenseService.createExpense(request, actor.getEmail()));

        assertEquals("Paid by user must be an authorized staff member", exception.getMessage());
    }

    @Test
    void createExpenseRejectsFutureExpenseDate() {
        BranchExpenseCreateRequest request = buildValidRequest();
        request.setExpenseDate(LocalDate.now().plusDays(1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> branchExpenseService.createExpense(request, actor.getEmail()));

        assertEquals("Future expense dates are not allowed", exception.getMessage());
    }

    @Test
    void getCurrentBranchExpensesReturnsOnlyCurrentBranchMonthRows() {
        mockAuthorizedContext();
        BranchExpense expense = new BranchExpense();
        expense.setId(55L);
        expense.setOrganization(organization);
        expense.setBranch(branch);
        expense.setExpenseName("Counter Expense");
        expense.setAmount(new BigDecimal("500.00"));
        expense.setExpenseType(ExpenseType.POCKET_CASH);
        expense.setExpenseDate(LocalDate.of(2026, 8, 8));
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(actor);
        expense.setCreatedAt(LocalDateTime.now());
        when(branchExpenseRepository.findByBranch_IdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanAndIsActiveTrueOrderByExpenseDateDescCreatedAtDescIdDesc(
                branch.getId(),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 9, 1)))
                .thenReturn(List.of(expense));

        List<BranchExpenseDto> result = branchExpenseService.getCurrentBranchExpenses(2026, 8, actor.getEmail());

        assertEquals(1, result.size());
        assertEquals("Counter Expense", result.get(0).getExpenseName());
        assertEquals("Prince", result.get(0).getPaidByName());
        verify(branchExpenseRepository)
                .findByBranch_IdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanAndIsActiveTrueOrderByExpenseDateDescCreatedAtDescIdDesc(
                        branch.getId(),
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 9, 1));
    }

    @Test
    void getEligiblePayersReturnsCurrentBranchAuthorizedStaffOnly() {
        mockAuthorizedContext();
        when(organizationUserRepository.findActiveStaffByOrganizationIdAndBranchIdAndRoles(
                organization.getId(),
                branch.getId(),
                List.of(UserRole.MANAGER, UserRole.ADMIN, UserRole.SUPER_ADMIN)))
                .thenReturn(List.of(projection(12, "Prince", UserRole.MANAGER)));

        var result = branchExpenseService.getEligiblePayers(actor.getEmail());

        assertEquals(1, result.size());
        assertEquals(12, result.get(0).getUserId());
        assertEquals("Prince", result.get(0).getName());
        assertEquals("MANAGER", result.get(0).getRole());
    }

    private BranchExpenseCreateRequest buildValidRequest() {
        BranchExpenseCreateRequest request = new BranchExpenseCreateRequest();
        request.setExpenseName("Tea");
        request.setAmount(new BigDecimal("100"));
        request.setExpenseType(ExpenseType.COUNTER_CASH);
        request.setPaidByUserId(paidBy.getId());
        request.setExpenseDate(LocalDate.now().minusDays(1));
        request.setNotes("test");
        return request;
    }

    private OrganizationUserRepository.ActiveBranchStaffProjection projection(
            Integer userId,
            String name,
            UserRole role) {
        return new OrganizationUserRepository.ActiveBranchStaffProjection() {
            @Override
            public Integer getUserId() {
                return userId;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public UserRole getRole() {
                return role;
            }
        };
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
        when(organizationRepository.findByIdAndIsActiveTrue(organization.getId())).thenReturn(Optional.of(organization));
        when(organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId()))
                .thenReturn(Optional.of(actorMembership));
        when(branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branch.getId(), organization.getId()))
                .thenReturn(Optional.of(branch));
    }
}
