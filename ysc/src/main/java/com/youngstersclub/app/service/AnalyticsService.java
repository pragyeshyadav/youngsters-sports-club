package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.TodayEarningsDuePlayerDto;
import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.dto.SettledPaymentDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final FrameRepository frameRepository;
    private final PaymentRepository paymentRepository;
    private final GameActivityService gameActivityService;
    private final com.youngstersclub.app.repository.UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public AnalyticsService(
            FrameRepository frameRepository,
            PaymentRepository paymentRepository,
            GameActivityService gameActivityService,
            com.youngstersclub.app.repository.UserRepository userRepository,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository) {
        this.frameRepository = frameRepository;
        this.paymentRepository = paymentRepository;
        this.gameActivityService = gameActivityService;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    public TodayEarningsResponseDto getTodayEarnings(String actorEmail) {
        return getEarningsForDate(TimeUtil.nowIST().toLocalDate(), actorEmail);
    }

    public TodayEarningsResponseDto getEarningsForDate(LocalDate requestedDate, String actorEmail) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDate selectedDate = requestedDate == null ? today : requestedDate;
        LocalDate oldestAllowedDate = today.minusDays(60);

        if (selectedDate.isAfter(today)) {
            throw new IllegalArgumentException("Future dates are not allowed");
        }

        if (selectedDate.isBefore(oldestAllowedDate)) {
            throw new IllegalArgumentException("Please select a date within the last 60 days");
        }

        AnalyticsBranchContext context = resolveAnalyticsContext(actorEmail);
        LocalDateTime startDateTime = selectedDate.atStartOfDay();
        LocalDateTime endDateTime = selectedDate.plusDays(1).atStartOfDay();

        List<FrameRepository.TodayEarningsProjection> rows = frameRepository.findEarningsAnalyticsByBranchAndDateRange(
                context.branch().getId(),
                startDateTime,
                endDateTime);
        BigDecimal activityEarnings = gameActivityService.getGrossEarningsBetween(
                startDateTime,
                endDateTime,
                context.branch().getId());
        BigDecimal activityDue = gameActivityService.getTotalUnpaidDueBetween(
                startDateTime,
                endDateTime,
                context.branch().getId());
        Map<Integer, TodayEarningsDuePlayerDto> activityDuePlayers = gameActivityService.getUnpaidDuePlayersByDate(
                selectedDate,
                context.branch().getId());
        List<SettledPaymentDto> settledPayments = paymentRepository
                .findSettledPaymentsByBranchAndReferenceDateBetween(
                        context.branch().getId(),
                        selectedDate,
                        selectedDate)
                .stream()
                .map(payment -> new SettledPaymentDto(
                        payment.getUserName(),
                        payment.getPaidAmount() == null ? BigDecimal.ZERO : payment.getPaidAmount(),
                        payment.getDiscount() == null ? BigDecimal.ZERO : payment.getDiscount(),
                        payment.getDate(),
                        payment.getPaymentMethod()))
                .toList();

        BigDecimal baseEarnings = rows.isEmpty() || rows.get(0).getTotalEarnings() == null
                ? BigDecimal.ZERO
                : rows.get(0).getTotalEarnings();
        BigDecimal baseDue = rows.isEmpty() || rows.get(0).getTotalDue() == null
                ? BigDecimal.ZERO
                : rows.get(0).getTotalDue();

        Map<Integer, TodayEarningsDuePlayerDto> duePlayersByUser = new LinkedHashMap<>();
        for (FrameRepository.TodayEarningsProjection row : rows) {
            if (row.getUserId() == null || row.getPlayerName() == null || row.getPlayerName().isBlank()) {
                continue;
            }
            duePlayersByUser.put(
                    row.getUserId(),
                    new TodayEarningsDuePlayerDto(
                            row.getUserId(),
                            row.getPlayerName(),
                            row.getDueAmount() == null ? BigDecimal.ZERO : row.getDueAmount()));
        }

        mergeActivityDuePlayers(duePlayersByUser, activityDuePlayers);

        return new TodayEarningsResponseDto(
                baseEarnings.add(activityEarnings),
                baseDue.add(activityDue),
                duePlayersByUser.values().stream().toList(),
                settledPayments);
    }

    private AnalyticsBranchContext resolveAnalyticsContext(String actorEmail) {
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
        if (!UserRole.MANAGER.name().equals(currentRole)
                && !UserRole.ADMIN.name().equals(currentRole)
                && !UserRole.SUPER_ADMIN.name().equals(currentRole)) {
            throw new SecurityException("You are not authorized to view manager earnings");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));

        Branch branch = branchRepository
                .findByIdAndOrganizationIdAndIsActiveTrue(
                        context.getCurrentBranch().getId(),
                        context.getCurrentOrganization().getId())
                .orElseThrow(() -> new IllegalArgumentException("Current branch not found"));

        boolean branchAccessible = membership.getBaseBranch() != null
                && branch.getId().equals(membership.getBaseBranch().getId());
        if (!branchAccessible) {
            branchAccessible = userBranchAccessRepository
                    .existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(membership.getId(), branch.getId());
        }
        if (!branchAccessible) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new AnalyticsBranchContext(actor, context.getCurrentOrganization().getId(), branch);
    }

    protected void mergeActivityDuePlayers(
            Map<Integer, TodayEarningsDuePlayerDto> duePlayersByUser,
            Map<Integer, TodayEarningsDuePlayerDto> activityDuePlayers) {
        if (duePlayersByUser == null || activityDuePlayers == null || activityDuePlayers.isEmpty()) {
            return;
        }

        activityDuePlayers.forEach((userId, activityPlayer) -> {
            if (userId == null || activityPlayer == null || activityPlayer.getDue() == null
                    || activityPlayer.getDue().compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }
            TodayEarningsDuePlayerDto existing = duePlayersByUser.get(userId);
            if (existing != null) {
                duePlayersByUser.put(
                        userId,
                        new TodayEarningsDuePlayerDto(
                                userId,
                                existing.getName(),
                                existing.getDue().add(activityPlayer.getDue())));
                return;
            }

            duePlayersByUser.put(
                    userId,
                    new TodayEarningsDuePlayerDto(
                            userId,
                            activityPlayer.getName(),
                            activityPlayer.getDue()));
        });
    }

    private record AnalyticsBranchContext(User actor, Long organizationId, Branch branch) {
    }
}
