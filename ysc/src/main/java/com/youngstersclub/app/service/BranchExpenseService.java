package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.BranchExpenseCreateRequest;
import com.youngstersclub.app.dto.BranchExpenseDto;
import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.ExpensePayerOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.BranchExpense;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchExpenseRepository;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchExpenseService {

    private static final Logger log = LoggerFactory.getLogger(BranchExpenseService.class);
    private static final List<UserRole> ELIGIBLE_PAYER_ROLES = List.of(
            UserRole.MANAGER,
            UserRole.ADMIN,
            UserRole.SUPER_ADMIN);

    private final BranchExpenseRepository branchExpenseRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final OrganizationUserRepository organizationUserRepository;
    private final BranchRepository branchRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final OrganizationRepository organizationRepository;

    public BranchExpenseService(
            BranchExpenseRepository branchExpenseRepository,
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            OrganizationUserRepository organizationUserRepository,
            BranchRepository branchRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            OrganizationRepository organizationRepository) {
        this.branchExpenseRepository = branchExpenseRepository;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.organizationUserRepository = organizationUserRepository;
        this.branchRepository = branchRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional(readOnly = true)
    public List<BranchExpenseDto> getCurrentBranchExpenses(int year, int month, String actorEmail) {
        validateMonthYear(year, month);
        ExpenseBranchContext context = resolveExpenseContext(actorEmail);
        YearMonth selectedMonth = YearMonth.of(year, month);
        LocalDate monthStart = selectedMonth.atDay(1);
        LocalDate nextMonthStart = selectedMonth.plusMonths(1).atDay(1);

        List<BranchExpenseDto> expenses = branchExpenseRepository
                .findByBranch_IdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanAndIsActiveTrueOrderByExpenseDateDescCreatedAtDescIdDesc(
                        context.branch().getId(),
                        monthStart,
                        nextMonthStart)
                .stream()
                .map(this::toDto)
                .toList();

        log.info(
                "action=LIST_BRANCH_EXPENSES organizationId={} branchId={} year={} month={} resultCount={}",
                context.organization().getId(),
                context.branch().getId(),
                year,
                month,
                expenses.size());

        return expenses;
    }

    @Transactional(readOnly = true)
    public List<ExpensePayerOptionDto> getEligiblePayers(String actorEmail) {
        ExpenseBranchContext context = resolveExpenseContext(actorEmail);
        return organizationUserRepository
                .findActiveStaffByOrganizationIdAndBranchIdAndRoles(
                        context.organization().getId(),
                        context.branch().getId(),
                        ELIGIBLE_PAYER_ROLES)
                .stream()
                .map(projection -> new ExpensePayerOptionDto(
                        projection.getUserId(),
                        projection.getName(),
                        projection.getRole() == null ? null : projection.getRole().name()))
                .toList();
    }

    @Transactional
    public BranchExpenseDto createExpense(BranchExpenseCreateRequest request, String actorEmail) {
        validateCreateRequest(request);
        ExpenseBranchContext context = resolveExpenseContext(actorEmail);
        User paidBy = resolveEligiblePayer(request.getPaidByUserId(), context);
        LocalDateTime now = TimeUtil.nowIST();

        BranchExpense expense = new BranchExpense();
        expense.setOrganization(context.organization());
        expense.setBranch(context.branch());
        expense.setExpenseName(request.getExpenseName().trim());
        expense.setAmount(request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP));
        expense.setExpenseType(request.getExpenseType());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setNotes(normalizeNotes(request.getNotes()));
        expense.setPaidBy(paidBy);
        expense.setCreatedBy(context.actor());
        expense.setCreatedAt(now);
        expense.setUpdatedAt(now);
        expense.setIsActive(true);

        BranchExpense saved = branchExpenseRepository.save(expense);

        log.info(
                "action=ADD_BRANCH_EXPENSE organizationId={} branchId={} expenseId={} expenseType={} amount={} paidByUserId={} createdByUserId={}",
                context.organization().getId(),
                context.branch().getId(),
                saved.getId(),
                saved.getExpenseType(),
                saved.getAmount(),
                paidBy.getId(),
                context.actor().getId());

        return toDto(saved);
    }

    protected void validateMonthYear(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
        int currentYear = TimeUtil.nowIST().getYear();
        if (year < currentYear - 5 || year > currentYear + 1) {
            throw new IllegalArgumentException("Year is out of allowed range");
        }
    }

    protected String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected BranchExpenseDto toDto(BranchExpense expense) {
        return new BranchExpenseDto(
                expense.getId(),
                expense.getExpenseName(),
                expense.getAmount(),
                expense.getExpenseType(),
                expense.getExpenseDate(),
                expense.getNotes(),
                expense.getPaidBy() == null ? null : expense.getPaidBy().getId(),
                expense.getPaidBy() == null ? null : expense.getPaidBy().getName(),
                expense.getCreatedBy() == null ? null : expense.getCreatedBy().getId(),
                expense.getCreatedBy() == null ? null : expense.getCreatedBy().getName(),
                expense.getBranch() == null ? null : expense.getBranch().getId(),
                expense.getBranch() == null ? null : expense.getBranch().getName(),
                expense.getCreatedAt());
    }

    protected User resolveEligiblePayer(Integer paidByUserId, ExpenseBranchContext context) {
        if (paidByUserId == null) {
            throw new IllegalArgumentException("Paid by selection is required");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(paidByUserId, context.organization().getId())
                .orElseThrow(() -> new SecurityException("Paid by user does not belong to the current organization"));

        if (membership.getRole() == null || !ELIGIBLE_PAYER_ROLES.contains(membership.getRole())) {
            throw new SecurityException("Paid by user must be an authorized staff member");
        }

        User paidBy = membership.getUser();
        if (paidBy == null || !Boolean.TRUE.equals(paidBy.getIsActive())) {
            throw new SecurityException("Paid by user is not active");
        }

        boolean hasBranchAccess = membership.getBaseBranch() != null
                && context.branch().getId().equals(membership.getBaseBranch().getId());
        if (!hasBranchAccess) {
            hasBranchAccess = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    context.branch().getId());
        }

        if (!hasBranchAccess) {
            throw new SecurityException("Paid by user does not have access to the current branch");
        }

        return paidBy;
    }

    protected void validateCreateRequest(BranchExpenseCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Expense request is required");
        }

        String expenseName = request.getExpenseName() == null ? "" : request.getExpenseName().trim();
        if (expenseName.isEmpty()) {
            throw new IllegalArgumentException("Expense name is required");
        }
        if (expenseName.length() > 200) {
            throw new IllegalArgumentException("Expense name cannot exceed 200 characters");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero");
        }
        if (request.getExpenseType() == null) {
            throw new IllegalArgumentException("Expense type is required");
        }
        if (request.getExpenseDate() == null) {
            throw new IllegalArgumentException("Expense date is required");
        }
        if (request.getExpenseDate().isAfter(TimeUtil.nowIST().toLocalDate())) {
            throw new IllegalArgumentException("Future expense dates are not allowed");
        }
    }

    protected ExpenseBranchContext resolveExpenseContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        OrganizationOptionDto currentOrganization = context.getCurrentOrganization();
        BranchOptionDto currentBranch = context.getCurrentBranch();
        if (currentOrganization == null || currentBranch == null) {
            throw new SecurityException("Current organization and branch context are required");
        }

        UserRole role = resolveRole(context.getCurrentRole(), actor.getRole());
        if (!ELIGIBLE_PAYER_ROLES.contains(role)) {
            throw new SecurityException("You are not authorized to manage branch expenses");
        }

        Organization organization = organizationRepository.findByIdAndIsActiveTrue(currentOrganization.getId())
                .orElseThrow(() -> new NoSuchElementException("Current organization not found"));
        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organization.getId())
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));
        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(currentBranch.getId(), organization.getId())
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

        return new ExpenseBranchContext(actor, organization, branch, membership, role);
    }

    protected UserRole resolveRole(String currentRole, UserRole fallbackRole) {
        if (currentRole == null || currentRole.isBlank()) {
            return fallbackRole;
        }
        return UserRole.valueOf(currentRole);
    }

    protected record ExpenseBranchContext(
            User actor,
            Organization organization,
            Branch branch,
            OrganizationUser membership,
            UserRole role) {
    }
}
