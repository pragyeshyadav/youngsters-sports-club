package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PaymentRequest;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.entity.Branch;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.BranchRepository;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.OrganizationUserRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserBranchAccessRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final OrganizationContextService organizationContextService;
    private final BranchRepository branchRepository;
    private final OrganizationUserRepository organizationUserRepository;
    private final UserBranchAccessRepository userBranchAccessRepository;
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;
    private final com.youngstersclub.app.repository.FramePlayerRepository framePlayerRepository;
    private final UserPaymentSummaryService userPaymentSummaryService;
    private final WhatsAppService whatsAppService;
    private final UserDueService userDueService;

    public PaymentService(
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            OrganizationContextService organizationContextService,
            BranchRepository branchRepository,
            OrganizationUserRepository organizationUserRepository,
            UserBranchAccessRepository userBranchAccessRepository,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService,
            com.youngstersclub.app.repository.FramePlayerRepository framePlayerRepository,
            UserPaymentSummaryService userPaymentSummaryService,
            WhatsAppService whatsAppService,
            UserDueService userDueService) {
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.organizationContextService = organizationContextService;
        this.branchRepository = branchRepository;
        this.organizationUserRepository = organizationUserRepository;
        this.userBranchAccessRepository = userBranchAccessRepository;
        this.consumableService = consumableService;
        this.kidsPlayService = kidsPlayService;
        this.framePlayerRepository = framePlayerRepository;
        this.userPaymentSummaryService = userPaymentSummaryService;
        this.whatsAppService = whatsAppService;
        this.userDueService = userDueService;
    }

    @Transactional
    public void settlePayment(PaymentRequest request, String actorEmail) {
        if (request == null || request.getUserId() == null || request.getAmount() == null) {
            throw new IllegalArgumentException("Payment details are required");
        }

        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        if (request.getMode() == null || request.getMode().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment mode is required");
        }

        BigDecimal discount = request.getDiscount() == null ? BigDecimal.ZERO : request.getDiscount();
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }

        PaymentBranchContext context = resolvePaymentContext(actorEmail);
        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getMode().trim().toUpperCase());
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        List<Frame> frames = frameRepository.findDueFramesByUserAndBranchOrderByStartTime(
                request.getUserId(),
                context.branch().getId());
        List<ConsumableOrder> consumableOrders = consumableService.getUnpaidOrders(
                request.getUserId(),
                context.branch().getId());

        BigDecimal totalOutstanding = pendingTotalDue(request.getUserId(), context.branch().getId());
        BigDecimal totalSettlement = request.getAmount().add(discount);

        if (totalSettlement.compareTo(totalOutstanding) > 0) {
            throw new IllegalArgumentException("Payment amount plus discount exceeds total due");
        }

        AllocationState allocationState = new AllocationState(request.getAmount(), discount);

        for (Frame frame : frames) {
            if (allocationState.isExhausted()) {
                break;
            }

            com.youngstersclub.app.entity.FramePlayer userFp = null;
            if (frame.getFramePlayers() != null) {
                userFp = frame.getFramePlayers().stream()
                        .filter(fp -> fp.getUser() != null && fp.getUser().getId().equals(request.getUserId()) && fp.getAmountDue() != null && fp.getAmountDue().compareTo(BigDecimal.ZERO) > 0)
                        .findFirst()
                        .orElse(null);
            }

            BigDecimal due = userFp != null ? userFp.getAmountDue() : frame.getPaymentDue();

            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal settlementAmount = allocationState.getRemainingSettlement().min(due);
            BigDecimal cashAmount = allocationState.allocateCash(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);

            Payment payment = new Payment();
            payment.setFrame(frame);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setReferenceDate(TimeUtil.nowIST().toLocalDate());
            payment.setBranch(context.branch());
            paymentRepository.save(payment);

            if (userFp != null) {
                BigDecimal updatedDue = due.subtract(settlementAmount);
                userFp.setAmountDue(updatedDue);
                userFp.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                framePlayerRepository.save(userFp);
                
                if (frame.getPaymentDue() != null) {
                    BigDecimal overallUpdated = frame.getPaymentDue().subtract(settlementAmount);
                    frame.setPaymentDue(overallUpdated);
                    frame.setPaymentStatus(overallUpdated.compareTo(BigDecimal.ZERO) <= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                    frameRepository.save(frame);
                }
            } else {
                BigDecimal updatedDue = due.subtract(settlementAmount);
                frame.setPaymentDue(updatedDue);
                frame.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0
                        ? PaymentStatus.PAID
                        : PaymentStatus.PARTIAL);
                frameRepository.save(frame);
            }
            userDueService.syncBranchDue(user, context.branch());
        }

        for (ConsumableOrder order : consumableOrders) {
            if (allocationState.isExhausted()) {
                break;
            }

            BigDecimal due = order.getTotalAmount();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal settlementAmount = allocationState.getRemainingSettlement().min(due);
            BigDecimal cashAmount = allocationState.allocateCash(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);

            Payment payment = new Payment();
            payment.setFrame(null);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setReferenceDate(TimeUtil.nowIST().toLocalDate());
            payment.setBranch(context.branch());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            order.setTotalAmount(updatedDue);
            order.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            consumableOrderRepository.save(order);
            userDueService.syncBranchDue(user, context.branch());
        }

        if (!allocationState.isExhausted()) {
            kidsPlayService.settleKidsSessions(
                    request.getUserId(),
                    context.branch(),
                    allocationState.getRemainingCash(),
                    allocationState.getRemainingDiscount(),
                    user,
                    paymentMethod);
        }

        registerPaymentSettlementNotification(user, request.getAmount(), discount, context.branch().getId());
    }

    @Transactional
    public void settlePaymentByDate(com.youngstersclub.app.dto.PaymentByDateRequest request, String actorEmail) {
        if (request == null || request.getUserId() == null || request.getPaidAmount() == null || request.getDate() == null) {
            throw new IllegalArgumentException("Payment details and date are required");
        }

        if (request.getPaidAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        if (request.getPaymentMode() == null || request.getPaymentMode().trim().isEmpty()) {
            throw new IllegalArgumentException("Payment mode is required");
        }

        BigDecimal discount = request.getDiscount() == null ? BigDecimal.ZERO : request.getDiscount();
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount cannot be negative");
        }

        PaymentBranchContext context = resolvePaymentContext(actorEmail);
        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getPaymentMode().trim().toUpperCase());
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        
        List<Frame> frames = frameRepository.findDueFramesByUserAndBranchOrderByStartTime(request.getUserId(), context.branch().getId()).stream()
                .filter(frame -> frame.getStartTime() != null && request.getDate().equals(frame.getStartTime().toLocalDate()))
                .toList();
        List<ConsumableOrder> consumableOrders = consumableService.getUnpaidOrdersByDate(request.getUserId(), request.getDate(), context.branch().getId());
        BigDecimal totalOutstanding = userPaymentSummaryService
                .getBranchPaymentSummaryByDate(request.getUserId(), request.getDate(), context.branch().getId())
                .getTotalDue();
        BigDecimal totalSettlement = request.getPaidAmount().add(discount);

        if (totalSettlement.compareTo(totalOutstanding) > 0) {
            throw new IllegalArgumentException("Payment amount plus discount exceeds total due for the selected date");
        }

        AllocationState allocationState = new AllocationState(request.getPaidAmount(), discount);

        for (Frame frame : frames) {
            if (allocationState.isExhausted()) break;

            com.youngstersclub.app.entity.FramePlayer userFp = null;
            if (frame.getFramePlayers() != null) {
                userFp = frame.getFramePlayers().stream()
                        .filter(fp -> fp.getUser() != null && fp.getUser().getId().equals(request.getUserId()) && fp.getAmountDue() != null && fp.getAmountDue().compareTo(BigDecimal.ZERO) > 0)
                        .findFirst()
                        .orElse(null);
            }

            BigDecimal due = userFp != null ? userFp.getAmountDue() : frame.getPaymentDue();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal settlementAmount = allocationState.getRemainingSettlement().min(due);
            BigDecimal cashAmount = allocationState.allocateCash(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);

            Payment payment = new Payment();
            payment.setFrame(frame);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setReferenceDate(request.getDate());
            payment.setBranch(context.branch());
            paymentRepository.save(payment);

            if (userFp != null) {
                BigDecimal updatedDue = due.subtract(settlementAmount);
                userFp.setAmountDue(updatedDue);
                userFp.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                framePlayerRepository.save(userFp);

                if (frame.getPaymentDue() != null) {
                    BigDecimal overallUpdated = frame.getPaymentDue().subtract(settlementAmount);
                    frame.setPaymentDue(overallUpdated);
                    frame.setPaymentStatus(overallUpdated.compareTo(BigDecimal.ZERO) <= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                    frameRepository.save(frame);
                }
            } else {
                BigDecimal updatedDue = due.subtract(settlementAmount);
                frame.setPaymentDue(updatedDue);
                frame.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
                frameRepository.save(frame);
            }
            userDueService.syncBranchDue(user, context.branch());
        }

        for (ConsumableOrder order : consumableOrders) {
            if (allocationState.isExhausted()) break;
            BigDecimal due = order.getTotalAmount();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal settlementAmount = allocationState.getRemainingSettlement().min(due);
            BigDecimal cashAmount = allocationState.allocateCash(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);

            Payment payment = new Payment();
            payment.setFrame(null);
            payment.setUser(user);
            payment.setAmount(cashAmount);
            payment.setDiscount(discountAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            payment.setReferenceDate(request.getDate());
            payment.setBranch(context.branch());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            order.setTotalAmount(updatedDue);
            order.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            consumableOrderRepository.save(order);
            userDueService.syncBranchDue(user, context.branch());
        }

        if (!allocationState.isExhausted()) {
            kidsPlayService.settleKidsSessionsByDate(
                    request.getUserId(),
                    context.branch(),
                    request.getDate(),
                    allocationState.getRemainingCash(),
                    allocationState.getRemainingDiscount(),
                    user,
                    paymentMethod);
        }

        registerPaymentSettlementNotification(user, request.getPaidAmount(), discount, context.branch().getId());
    }

    private void registerPaymentSettlementNotification(User user, BigDecimal paidAmount, BigDecimal discountAmount, Long branchId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            triggerPaymentSettlementNotification(user, paidAmount, discountAmount, branchId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                triggerPaymentSettlementNotification(user, paidAmount, discountAmount, branchId);
            }
        });
    }

    private void triggerPaymentSettlementNotification(User user, BigDecimal paidAmount, BigDecimal discountAmount, Long branchId) {
        try {
            BigDecimal remainingDue = branchId == null
                    ? BigDecimal.ZERO
                    : userPaymentSummaryService.getBranchPaymentSummary(user.getId(), branchId).getTotalDue();
            whatsAppService.sendPaymentSettlementMessage(user, paidAmount, discountAmount, remainingDue);
        } catch (Exception ex) {
            log.warn("WhatsApp settlement notification failed for userId: {}. Reason: {}", user.getId(), ex.getMessage());
        }
    }

    private BigDecimal pendingTotalDue(Integer userId, Long branchId) {
        return userPaymentSummaryService.getBranchPaymentSummary(userId, branchId).getTotalDue();
    }

    private PaymentBranchContext resolvePaymentContext(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Actor email is required");
        }

        User actor = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Actor not found"));
        OrganizationContextDto context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current organization and branch context are required");
        }
        String currentRole = context.getCurrentRole() == null ? "" : context.getCurrentRole().trim();
        if (!UserRole.MANAGER.name().equals(currentRole)
                && !UserRole.ADMIN.name().equals(currentRole)
                && !UserRole.SUPER_ADMIN.name().equals(currentRole)) {
            throw new SecurityException("You are not authorized to settle payments");
        }

        OrganizationUser membership = organizationUserRepository
                .findByUserIdAndOrganizationIdAndIsActiveTrue(actor.getId(), context.getCurrentOrganization().getId())
                .orElseThrow(() -> new SecurityException("Active organization membership not found"));

        Branch branch = branchRepository.findByIdAndOrganizationIdAndIsActiveTrue(
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
        return new PaymentBranchContext(actor, context.getCurrentOrganization().getId(), branch);
    }

    private record PaymentBranchContext(User actor, Long organizationId, Branch branch) {}

    private static final class AllocationState {
        private BigDecimal remainingCash;
        private BigDecimal remainingDiscount;

        private AllocationState(BigDecimal cash, BigDecimal discount) {
            this.remainingCash = cash == null ? BigDecimal.ZERO : cash;
            this.remainingDiscount = discount == null ? BigDecimal.ZERO : discount;
        }

        private BigDecimal getRemainingCash() {
            return remainingCash;
        }

        private BigDecimal getRemainingDiscount() {
            return remainingDiscount;
        }

        private BigDecimal getRemainingSettlement() {
            return remainingCash.add(remainingDiscount);
        }

        private boolean isExhausted() {
            return getRemainingSettlement().compareTo(BigDecimal.ZERO) <= 0;
        }

        private BigDecimal allocateCash(BigDecimal settlementAmount) {
            BigDecimal cashAmount = remainingCash.min(settlementAmount);
            BigDecimal discountAmount = settlementAmount.subtract(cashAmount);
            remainingCash = remainingCash.subtract(cashAmount);
            remainingDiscount = remainingDiscount.subtract(discountAmount);
            return cashAmount;
        }
    }
}
