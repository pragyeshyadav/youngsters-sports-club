package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.KidsSessionEndRequest;
import com.youngstersclub.app.dto.PendingKidsPlayBreakdownDto;
import com.youngstersclub.app.dto.KidsSessionResponseDto;
import com.youngstersclub.app.dto.KidsSessionStartRequest;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.Child;
import com.youngstersclub.app.entity.KidsPlaySession;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class KidsPlayService {

    private static final String KIDS_TABLE_NAME = "Kids Ocean Dream Land";

    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final SnookerTableRepository snookerTableRepository;
    private final UserRepository userRepository;
    private final ChildService childService;
    private final PaymentRepository paymentRepository;
    private final GameActivityService gameActivityService;
    private final UserDueService userDueService;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;

    public KidsPlayService(
            KidsPlaySessionRepository kidsPlaySessionRepository,
            SnookerTableRepository snookerTableRepository,
            UserRepository userRepository,
            ChildService childService,
            PaymentRepository paymentRepository,
            GameActivityService gameActivityService,
            UserDueService userDueService,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository) {
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.snookerTableRepository = snookerTableRepository;
        this.userRepository = userRepository;
        this.childService = childService;
        this.paymentRepository = paymentRepository;
        this.gameActivityService = gameActivityService;
        this.userDueService = userDueService;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
    }

    @Transactional
    public KidsSessionResponseDto startSession(KidsSessionStartRequest request, String actorEmail) {
        if (request == null || request.getParentUserId() == null || request.getChildId() == null) {
            throw new IllegalArgumentException("Parent and child are required");
        }

        KidsBranchContext context = resolveKidsBranchContext(actorEmail);
        if (kidsPlaySessionRepository.findActiveByChildId(request.getChildId()).isPresent()) {
            throw new IllegalArgumentException("An active play session for this child already exists");
        }

        User parent = userRepository.findById(request.getParentUserId()).orElseThrow();
        validateParentMembership(parent.getId(), context.organizationId());
        Child child = childService.getOwnedChild(request.getChildId(), request.getParentUserId());
        SnookerTable kidsTable = snookerTableRepository.findByBranch_IdAndTableNameIgnoreCase(
                        context.branch().getId(),
                        KIDS_TABLE_NAME)
                .orElseThrow(() -> new IllegalArgumentException("Kids Ocean Dreamland pricing is not configured"));
        if (!Boolean.TRUE.equals(kidsTable.getIsActive())) {
            throw new IllegalArgumentException("Kids Ocean Dreamland pricing is not configured");
        }

        KidsPlaySession session = new KidsPlaySession();
        session.setChild(child);
        session.setParentUser(parent);
        session.setBranch(context.branch());
        session.setStartTime(TimeUtil.nowIST());
        session.setRatePerMinute(kidsTable.getRatePerMinute());
        session.setPaymentStatus("UNPAID");
        session.setStatus("STARTED");

        KidsPlaySession savedSession = kidsPlaySessionRepository.save(session);
        userDueService.syncBranchDue(savedSession.getParentUser(), savedSession.getBranch());
        return toDto(savedSession);
    }

    @Transactional
    public KidsSessionResponseDto endSession(KidsSessionEndRequest request, String actorEmail) {
        if (request == null || request.getSessionId() == null || request.getParentUserId() == null) {
            throw new IllegalArgumentException("Session and parent are required");
        }

        KidsBranchContext context = resolveKidsBranchContext(actorEmail);
        KidsPlaySession session = kidsPlaySessionRepository.findByIdAndBranch_Id(
                        request.getSessionId(),
                        context.branch().getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        validateOwnership(session, request.getParentUserId());

        if (session.getEndTime() != null) {
            throw new IllegalArgumentException("Session already ended");
        }

        LocalDateTime endTime = TimeUtil.nowIST();
        long duration = Duration.between(session.getStartTime(), endTime).toMinutes();
        if (duration <= 0) {
            duration = 1;
        }

        BigDecimal totalAmount = session.getRatePerMinute().multiply(BigDecimal.valueOf(duration));
        session.setEndTime(endTime);
        session.setDurationMinutes((int) duration);
        session.setTotalAmount(totalAmount);
        session.setPaymentStatus("UNPAID");
        session.setStatus("ENDED");

        return toDto(kidsPlaySessionRepository.save(session));
    }

    @Transactional
    public KidsSessionResponseDto rejectSession(Long sessionId, String actorEmail) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required");
        }

        KidsBranchContext context = resolveKidsBranchContext(actorEmail);
        KidsPlaySession session = kidsPlaySessionRepository.findByIdAndBranch_Id(sessionId, context.branch().getId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        if (session.getEndTime() != null) {
            throw new IllegalArgumentException("Session already ended");
        }

        session.setEndTime(TimeUtil.nowIST());
        session.setDurationMinutes(0);
        session.setTotalAmount(BigDecimal.ZERO);
        session.setPaymentStatus("CANCELLED");
        session.setStatus("CANCELLED");

        return toDto(kidsPlaySessionRepository.save(session));
    }

    public List<KidsSessionResponseDto> getActiveSessions(Integer parentUserId, String actorEmail) {
        if (parentUserId == null) {
            return List.of();
        }
        KidsBranchContext context = resolveKidsBranchContext(actorEmail);
        validateParentMembership(parentUserId, context.organizationId());
        return kidsPlaySessionRepository.findActiveByParentUserIdAndBranchId(parentUserId, context.branch().getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public List<KidsSessionResponseDto> getAllActiveSessions(String actorEmail) {
        KidsBranchContext context = resolveKidsBranchContext(actorEmail);
        return kidsPlaySessionRepository.findAllActiveSessionsByBranchId(context.branch().getId()).stream()
                .map(this::toDto)
                .toList();
    }

    public BigDecimal getKidsDue(Integer parentUserId) {
        if (parentUserId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sessionDue = kidsPlaySessionRepository.getTotalUnpaidDueByParentUserId(parentUserId);
        BigDecimal activityDue = gameActivityService.getActivityDue(parentUserId);
        return (sessionDue == null ? BigDecimal.ZERO : sessionDue).add(activityDue);
    }

    public BigDecimal getKidsDue(Integer parentUserId, Long branchId) {
        if (parentUserId == null || branchId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sessionDue = getUnpaidSessions(parentUserId, branchId).stream()
                .map(KidsPlaySession::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal activityDue = gameActivityService.getActivityDueByDateRange(parentUserId, null, null, branchId);
        return sessionDue.add(activityDue);
    }

    public BigDecimal getKidsDueByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sessionDue = getUnpaidSessionsByDate(parentUserId, selectedDate).stream()
                .map(KidsPlaySession::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sessionDue.add(gameActivityService.getActivityDueByDate(parentUserId, selectedDate));
    }

    public BigDecimal getKidsDueByDate(Integer parentUserId, LocalDate selectedDate, Long branchId) {
        if (parentUserId == null || selectedDate == null || branchId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sessionDue = getUnpaidSessionsByDate(parentUserId, selectedDate, branchId).stream()
                .map(KidsPlaySession::getTotalAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sessionDue.add(gameActivityService.getActivityDueByDate(parentUserId, selectedDate, branchId));
    }

    public Map<Integer, BigDecimal> getKidsDueMap(List<Integer> userIds) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return dues;
        }

        kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIds(userIds).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        gameActivityService.getActivityDueMap(userIds).forEach((userId, amount) ->
                dues.merge(userId, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add));
        return dues;
    }

    public Map<Integer, BigDecimal> getKidsDueMap(List<Integer> userIds, Long branchId) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty() || branchId == null) {
            return dues;
        }

        getKidsSessionDueMap(userIds, branchId).forEach((userId, amount) ->
                dues.put(userId, amount == null ? BigDecimal.ZERO : amount));
        gameActivityService.getActivityDueMap(userIds, branchId).forEach((userId, amount) ->
                dues.merge(userId, amount == null ? BigDecimal.ZERO : amount, BigDecimal::add));
        return dues;
    }

    public Map<Integer, BigDecimal> getKidsSessionDueMap(List<Integer> userIds, Long branchId) {
        Map<Integer, BigDecimal> dues = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty() || branchId == null) {
            return dues;
        }

        kidsPlaySessionRepository.getTotalUnpaidDueByParentUserIdsAndBranchId(userIds, branchId).forEach(projection ->
                dues.put(projection.getUserId(), projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        return dues;
    }

    public List<KidsPlaySession> getUnpaidSessions(Integer parentUserId) {
        if (parentUserId == null) {
            return List.of();
        }
        return kidsPlaySessionRepository.findUnpaidByParentUserIdOrderByStartTime(parentUserId);
    }

    public List<KidsPlaySession> getUnpaidSessions(Integer parentUserId, Long branchId) {
        if (parentUserId == null || branchId == null) {
            return List.of();
        }
        return kidsPlaySessionRepository.findUnpaidByParentUserIdAndBranchIdOrderByStartTime(parentUserId, branchId);
    }

    public List<KidsPlaySession> getUnpaidSessionsByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return List.of();
        }
        return getUnpaidSessions(parentUserId).stream()
                .filter(session -> session.getStartTime() != null && selectedDate.equals(session.getStartTime().toLocalDate()))
                .toList();
    }

    public List<KidsPlaySession> getUnpaidSessionsByDate(Integer parentUserId, LocalDate selectedDate, Long branchId) {
        if (parentUserId == null || selectedDate == null || branchId == null) {
            return List.of();
        }
        return getUnpaidSessions(parentUserId, branchId).stream()
                .filter(session -> session.getStartTime() != null && selectedDate.equals(session.getStartTime().toLocalDate()))
                .toList();
    }

    public List<PendingKidsPlayBreakdownDto> getKidsDueBreakdownByDate(Integer parentUserId, LocalDate selectedDate) {
        if (parentUserId == null || selectedDate == null) {
            return List.of();
        }
        List<PendingKidsPlayBreakdownDto> sessions = getUnpaidSessionsByDate(parentUserId, selectedDate).stream()
                .map(session -> new PendingKidsPlayBreakdownDto(
                        session.getId(),
                        session.getChild() != null ? session.getChild().getName() : null,
                        session.getEndTime() != null ? session.getEndTime() : session.getStartTime(),
                        session.getTotalAmount()))
                .toList();
        List<PendingKidsPlayBreakdownDto> activities = gameActivityService.getActivityDueBreakdownByDate(parentUserId, selectedDate);

        return java.util.stream.Stream.concat(sessions.stream(), activities.stream())
                .sorted(java.util.Comparator.comparing(
                        PendingKidsPlayBreakdownDto::getDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    public List<PendingKidsPlayBreakdownDto> getKidsDueBreakdownByDate(
            Integer parentUserId,
            LocalDate selectedDate,
            Long branchId) {
        if (parentUserId == null || selectedDate == null || branchId == null) {
            return List.of();
        }
        List<PendingKidsPlayBreakdownDto> sessions = getUnpaidSessionsByDate(parentUserId, selectedDate, branchId).stream()
                .map(session -> new PendingKidsPlayBreakdownDto(
                        session.getId(),
                        session.getChild() != null ? session.getChild().getName() : null,
                        session.getEndTime() != null ? session.getEndTime() : session.getStartTime(),
                        session.getTotalAmount()))
                .toList();
        List<PendingKidsPlayBreakdownDto> activities = gameActivityService.getActivityDueBreakdownByDate(parentUserId, selectedDate, branchId);

        return java.util.stream.Stream.concat(sessions.stream(), activities.stream())
                .sorted(java.util.Comparator.comparing(
                        PendingKidsPlayBreakdownDto::getDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    @Transactional
    public BigDecimal settleKidsSessions(Integer parentUserId, BigDecimal amount, User user, PaymentMethod paymentMethod) {
        return settleKidsSessions(parentUserId, amount, BigDecimal.ZERO, user, paymentMethod);
    }

    @Transactional
    public BigDecimal settleKidsSessions(
            Integer parentUserId,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        return settleKidsSessions(parentUserId, null, amount, discount, user, paymentMethod);
    }

    @Transactional
    public BigDecimal settleKidsSessions(
            Integer parentUserId,
            Branch branch,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = BigDecimal.ZERO;
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (amount.add(discount).compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainingCash = amount;
        BigDecimal remainingDiscount = discount;
        BigDecimal remainingSettlement = amount.add(discount);
        List<KidsPlaySession> sessions = branch == null
                ? getUnpaidSessions(parentUserId)
                : getUnpaidSessions(parentUserId, branch.getId());

        for (KidsPlaySession session : sessions) {
            if (remainingSettlement.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal due = session.getTotalAmount();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal settlementAmount = remainingSettlement.min(due);
            BigDecimal cashAmount = remainingCash.min(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);
            Payment payment = new Payment();
            payment.setFrame(null);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setBranch(session.getBranch());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            session.setTotalAmount(updatedDue);
            session.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            kidsPlaySessionRepository.save(session);
            userDueService.syncBranchDue(session.getParentUser(), session.getBranch());
            remainingCash = remainingCash.subtract(cashAmount);
            remainingDiscount = remainingDiscount.subtract(discountAmount);
            remainingSettlement = remainingCash.add(remainingDiscount);
        }

        if (remainingSettlement.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal activitySettled = gameActivityService.settleActivityOrders(
                    parentUserId,
                    branch,
                    remainingCash,
                    remainingDiscount,
                    user,
                    paymentMethod);
            remainingSettlement = remainingSettlement.subtract(activitySettled);
        }

        return amount.add(discount).subtract(remainingSettlement);
    }

    @Transactional
    public BigDecimal settleKidsSessionsByDate(
            Integer parentUserId,
            LocalDate selectedDate,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        return settleKidsSessionsByDate(parentUserId, null, selectedDate, amount, discount, user, paymentMethod);
    }

    @Transactional
    public BigDecimal settleKidsSessionsByDate(
            Integer parentUserId,
            Branch branch,
            LocalDate selectedDate,
            BigDecimal amount,
            BigDecimal discount,
            User user,
            PaymentMethod paymentMethod) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            amount = BigDecimal.ZERO;
        }
        if (discount == null || discount.compareTo(BigDecimal.ZERO) < 0) {
            discount = BigDecimal.ZERO;
        }
        if (amount.add(discount).compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal remainingCash = amount;
        BigDecimal remainingDiscount = discount;
        BigDecimal remainingSettlement = amount.add(discount);

        List<KidsPlaySession> sessions = branch == null
                ? getUnpaidSessionsByDate(parentUserId, selectedDate)
                : getUnpaidSessionsByDate(parentUserId, selectedDate, branch.getId());

        for (KidsPlaySession session : sessions) {
            if (remainingSettlement.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal due = session.getTotalAmount();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal settlementAmount = remainingSettlement.min(due);
            BigDecimal cashAmount = remainingCash.min(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);
            Payment payment = new Payment();
            payment.setFrame(null);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setBranch(session.getBranch());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            session.setTotalAmount(updatedDue);
            session.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            kidsPlaySessionRepository.save(session);
            userDueService.syncBranchDue(session.getParentUser(), session.getBranch());
            remainingCash = remainingCash.subtract(cashAmount);
            remainingDiscount = remainingDiscount.subtract(discountAmount);
            remainingSettlement = remainingCash.add(remainingDiscount);
        }

        if (remainingSettlement.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal activitySettled = gameActivityService.settleActivityOrdersByDate(
                    parentUserId,
                    branch,
                    selectedDate,
                    remainingCash,
                    remainingDiscount,
                    user,
                    paymentMethod);
            remainingSettlement = remainingSettlement.subtract(activitySettled);
        }

        return amount.add(discount).subtract(remainingSettlement);
    }

    private void validateOwnership(KidsPlaySession session, Integer parentUserId) {
        if (session.getParentUser() == null || session.getParentUser().getId() == null
                || !session.getParentUser().getId().equals(parentUserId)) {
            throw new IllegalArgumentException("Session does not belong to this parent");
        }
    }

    private void validateParentMembership(Integer parentUserId, Long organizationId) {
        organizationUserRepository.findByUserIdAndOrganizationIdAndIsActiveTrue(parentUserId, organizationId)
                .orElseThrow(() -> new SecurityException("Parent does not belong to the current organization"));
    }

    private KidsBranchContext resolveKidsBranchContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new SecurityException("Authenticated user email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new SecurityException("Authenticated user not found"));
        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new SecurityException("Active organization and branch context are required");
        }

        Long organizationId = context.getCurrentOrganization().getId();
        Long branchId = context.getCurrentBranch().getId();
        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), organizationId)
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));
        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(branchId, organizationId)
                .orElseThrow(() -> new SecurityException("Current branch is unavailable"));

        boolean hasAccess = membership.getBaseBranch() != null
                && branchId.equals(membership.getBaseBranch().getId());
        if (!hasAccess) {
            hasAccess = userBranchAccessRepository.existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(
                    membership.getId(),
                    branchId);
        }
        if (!hasAccess) {
            throw new SecurityException("You do not have access to the current branch");
        }

        return new KidsBranchContext(actor, organizationId, branch);
    }

    private record KidsBranchContext(User actor, Long organizationId, Branch branch) {
    }

    private KidsSessionResponseDto toDto(KidsPlaySession session) {
        return new KidsSessionResponseDto(
                session.getId(),
                session.getChild() != null ? session.getChild().getId() : null,
                session.getChild() != null ? session.getChild().getName() : null,
                session.getParentUser() != null ? session.getParentUser().getId() : null,
                session.getParentUser() != null ? session.getParentUser().getName() : null,
                session.getStartTime(),
                session.getEndTime(),
                session.getDurationMinutes(),
                session.getRatePerMinute(),
                session.getTotalAmount(),
                session.getPaymentStatus(),
                session.getStatus());
    }
}
