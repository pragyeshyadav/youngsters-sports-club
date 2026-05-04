package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.KidsSessionEndRequest;
import com.youngstersclub.app.dto.KidsSessionResponseDto;
import com.youngstersclub.app.dto.KidsSessionStartRequest;
import com.youngstersclub.app.entity.Child;
import com.youngstersclub.app.entity.KidsPlaySession;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.SnookerTable;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.SnookerTableRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class KidsPlayService {

    private static final String KIDS_TABLE_NAME = "Kids Ocean Dream Land";

    private final KidsPlaySessionRepository kidsPlaySessionRepository;
    private final SnookerTableRepository snookerTableRepository;
    private final UserRepository userRepository;
    private final ChildService childService;
    private final PaymentRepository paymentRepository;

    public KidsPlayService(
            KidsPlaySessionRepository kidsPlaySessionRepository,
            SnookerTableRepository snookerTableRepository,
            UserRepository userRepository,
            ChildService childService,
            PaymentRepository paymentRepository) {
        this.kidsPlaySessionRepository = kidsPlaySessionRepository;
        this.snookerTableRepository = snookerTableRepository;
        this.userRepository = userRepository;
        this.childService = childService;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public KidsSessionResponseDto startSession(KidsSessionStartRequest request) {
        if (request == null || request.getParentUserId() == null || request.getChildId() == null) {
            throw new IllegalArgumentException("Parent and child are required");
        }

        if (kidsPlaySessionRepository.findActiveByChildId(request.getChildId()).isPresent()) {
            throw new IllegalArgumentException("An active play session for this child already exists");
        }

        User parent = userRepository.findById(request.getParentUserId()).orElseThrow();
        Child child = childService.getOwnedChild(request.getChildId(), request.getParentUserId());
        SnookerTable kidsTable = snookerTableRepository.findFirstByTableNameIgnoreCase(KIDS_TABLE_NAME)
                .orElseThrow(() -> new IllegalArgumentException("Kids Ocean Dreamland pricing is not configured"));

        KidsPlaySession session = new KidsPlaySession();
        session.setChild(child);
        session.setParentUser(parent);
        session.setStartTime(TimeUtil.nowIST());
        session.setRatePerMinute(kidsTable.getRatePerMinute());
        session.setPaymentStatus("UNPAID");
        session.setStatus("STARTED");

        return toDto(kidsPlaySessionRepository.save(session));
    }

    @Transactional
    public KidsSessionResponseDto endSession(KidsSessionEndRequest request) {
        if (request == null || request.getSessionId() == null || request.getParentUserId() == null) {
            throw new IllegalArgumentException("Session and parent are required");
        }

        KidsPlaySession session = kidsPlaySessionRepository.findById(request.getSessionId()).orElseThrow();
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
    public KidsSessionResponseDto rejectSession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID is required");
        }

        KidsPlaySession session = kidsPlaySessionRepository.findById(sessionId).orElseThrow();
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

    public List<KidsSessionResponseDto> getActiveSessions(Integer parentUserId) {
        if (parentUserId == null) {
            return List.of();
        }
        return kidsPlaySessionRepository.findActiveByParentUserId(parentUserId).stream()
                .map(this::toDto)
                .toList();
    }

    public List<KidsSessionResponseDto> getAllActiveSessions() {
        return kidsPlaySessionRepository.findAllActiveSessions().stream()
                .map(this::toDto)
                .toList();
    }

    public BigDecimal getKidsDue(Integer parentUserId) {
        if (parentUserId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal due = kidsPlaySessionRepository.getTotalUnpaidDueByParentUserId(parentUserId);
        return due == null ? BigDecimal.ZERO : due;
    }

    public List<KidsPlaySession> getUnpaidSessions(Integer parentUserId) {
        if (parentUserId == null) {
            return List.of();
        }
        return kidsPlaySessionRepository.findUnpaidByParentUserIdOrderByStartTime(parentUserId);
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
        for (KidsPlaySession session : getUnpaidSessions(parentUserId)) {
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
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            session.setTotalAmount(updatedDue);
            session.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            kidsPlaySessionRepository.save(session);
            remainingCash = remainingCash.subtract(cashAmount);
            remainingDiscount = remainingDiscount.subtract(discountAmount);
            remainingSettlement = remainingCash.add(remainingDiscount);
        }

        return amount.add(discount).subtract(remainingSettlement);
    }

    private void validateOwnership(KidsPlaySession session, Integer parentUserId) {
        if (session.getParentUser() == null || session.getParentUser().getId() == null
                || !session.getParentUser().getId().equals(parentUserId)) {
            throw new IllegalArgumentException("Session does not belong to this parent");
        }
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
