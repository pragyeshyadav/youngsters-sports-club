package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {

    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final GameActivityService gameActivityService;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public AdminAnalyticsService(
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            KidsPlaySessionRepository kidsPlaySessionRepository,
            GameActivityService gameActivityService,
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository) {
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.gameActivityService = gameActivityService;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    public AdminMonthlyEarningsDto getMonthlyEarnings(int month, int year, String actorEmail) {
        validateMonthYear(month, year);
        AdminAnalyticsContext context = resolveAdminAnalyticsContext(actorEmail);

        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDate selectedMonthStart = LocalDate.of(year, month, 1);
        LocalDate selectedMonthEnd = selectedMonthStart.withDayOfMonth(selectedMonthStart.lengthOfMonth());
        LocalDate selectedEffectiveEnd = selectedMonthEnd.isAfter(today) ? today : selectedMonthEnd;

        LocalDate previousMonthStart = selectedMonthStart.minusMonths(1);
        LocalDate previousMonthEnd = previousMonthStart.withDayOfMonth(previousMonthStart.lengthOfMonth());
        LocalDate previousEffectiveEnd = previousMonthEnd.isAfter(today) ? today : previousMonthEnd;

        BigDecimal snookerEarnings = sumOrZero(frameRepository.getCompletedEarningsBetweenAndBranchId(
                context.branch().getId(),
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal consumableEarnings = sumOrZero(consumableOrderRepository.getPaidEarningsBetweenAndBranchId(
                context.branch().getId(),
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal kidsZoneEarnings = sumOrZero(kidsPlaySessionRepository.getPaidEarningsBetweenAndBranchId(
                context.branch().getId(),
                selectedMonthStart.atStartOfDay(),
                selectedEffectiveEnd.plusDays(1).atStartOfDay()))
                .add(gameActivityService.getPaidEarningsBetween(
                        selectedMonthStart.atStartOfDay(),
                        selectedEffectiveEnd.plusDays(1).atStartOfDay(),
                        context.branch().getId()));
        Map<String, BigDecimal> snookerTableBreakdown = frameRepository
                .getCompletedEarningsByTableBetweenAndBranchId(
                        context.branch().getId(),
                        selectedMonthStart.atStartOfDay(),
                        selectedEffectiveEnd.plusDays(1).atStartOfDay())
                .stream()
                .collect(
                        LinkedHashMap::new,
                        (map, row) -> map.put(row.getTableName(), sumOrZero(row.getTotal())),
                        LinkedHashMap::putAll);

        BigDecimal currentMonthTotal = snookerEarnings.add(consumableEarnings).add(kidsZoneEarnings);

        BigDecimal previousSnooker = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(frameRepository.getCompletedEarningsBetweenAndBranchId(
                        context.branch().getId(),
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal previousConsumables = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(consumableOrderRepository.getPaidEarningsBetweenAndBranchId(
                        context.branch().getId(),
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay()));
        BigDecimal previousKids = previousEffectiveEnd.isBefore(previousMonthStart)
                ? BigDecimal.ZERO
                : sumOrZero(kidsPlaySessionRepository.getPaidEarningsBetweenAndBranchId(
                        context.branch().getId(),
                        previousMonthStart.atStartOfDay(),
                        previousEffectiveEnd.plusDays(1).atStartOfDay())).add(gameActivityService.getPaidEarningsBetween(
                                previousMonthStart.atStartOfDay(),
                                previousEffectiveEnd.plusDays(1).atStartOfDay(),
                                context.branch().getId()));

        BigDecimal previousMonthTotal = previousSnooker.add(previousConsumables).add(previousKids);

        return new AdminMonthlyEarningsDto(
                currentMonthTotal,
                previousMonthTotal,
                snookerEarnings,
                snookerTableBreakdown,
                consumableEarnings,
                kidsZoneEarnings);
    }

    protected AdminAnalyticsContext resolveAdminAnalyticsContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Actor email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Actor not found"));

        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current organization and branch context are required");
        }

        String currentRole = context.getCurrentRole() == null ? "" : context.getCurrentRole().trim();
        if (!UserRole.ADMIN.name().equals(currentRole) && !UserRole.SUPER_ADMIN.name().equals(currentRole)) {
            throw new SecurityException("You are not authorized to view admin earnings");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));

        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(),
                        context.getCurrentOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Current branch not found"));

        if (!hasBranchAccess(membership, branch)) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new AdminAnalyticsContext(actor, context.getCurrentOrganization().getId(), branch);
    }

    protected boolean hasBranchAccess(OrganizationUser membership, Branch branch) {
        if (membership == null || branch == null) {
            return false;
        }

        boolean branchAccessible = membership.getBaseBranch() != null
                && branch.getId().equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository
                    .existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId());
        }
        return branchAccessible;
    }

    protected void validateMonthYear(int month, int year) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }

        int currentYear = TimeUtil.nowIST().getYear();
        if (year < currentYear - 1 || year > currentYear) {
            throw new IllegalArgumentException("Year must be current year or previous year");
        }
    }

    protected BigDecimal sumOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    protected record AdminAnalyticsContext(User actor, Long organizationId, Branch branch) {
    }
}
