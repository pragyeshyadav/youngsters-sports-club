package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PaymentRequest;
import com.youngstersclub.app.entity.ConsumableOrder;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.PaymentRepository;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final FrameRepository frameRepository;
    private final ConsumableOrderRepository consumableOrderRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final KidsPlayService kidsPlayService;

    public PaymentService(
            FrameRepository frameRepository,
            ConsumableOrderRepository consumableOrderRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            KidsPlayService kidsPlayService) {
        this.frameRepository = frameRepository;
        this.consumableOrderRepository = consumableOrderRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.kidsPlayService = kidsPlayService;
    }

    @Transactional
    public void settlePayment(PaymentRequest request) {
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

        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getMode().trim().toUpperCase());
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        List<Frame> frames = frameRepository.findDueFramesByUserOrderByStartTime(request.getUserId());
        List<ConsumableOrder> consumableOrders = consumableOrderRepository.findByUserIdAndPaymentStatus(
                request.getUserId(),
                "UNPAID");

        BigDecimal totalFrameOutstanding = frames.stream()
                .map(Frame::getPaymentDue)
                .filter(due -> due != null && due.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalConsumableOutstanding = consumableOrders.stream()
                .map(ConsumableOrder::getTotalAmount)
                .filter(due -> due != null && due.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalKidsOutstanding = kidsPlayService.getKidsDue(request.getUserId());
        BigDecimal totalOutstanding = totalFrameOutstanding.add(totalConsumableOutstanding).add(totalKidsOutstanding);
        BigDecimal totalSettlement = request.getAmount().add(discount);

        if (totalSettlement.compareTo(totalOutstanding) > 0) {
            throw new IllegalArgumentException("Payment amount plus discount exceeds total due");
        }

        AllocationState allocationState = new AllocationState(request.getAmount(), discount);

        for (Frame frame : frames) {
            if (allocationState.isExhausted()) {
                break;
            }

            BigDecimal due = frame.getPaymentDue();
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
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            frame.setPaymentDue(updatedDue);
            frame.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0
                    ? PaymentStatus.PAID
                    : PaymentStatus.PARTIAL);
            frameRepository.save(frame);
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
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            order.setTotalAmount(updatedDue);
            order.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            consumableOrderRepository.save(order);
        }

        if (!allocationState.isExhausted()) {
            kidsPlayService.settleKidsSessions(
                    request.getUserId(),
                    allocationState.getRemainingCash(),
                    allocationState.getRemainingDiscount(),
                    user,
                    paymentMethod);
        }
    }

    @Transactional
    public void settlePaymentByDate(com.youngstersclub.app.dto.PaymentByDateRequest request) {
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

        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getPaymentMode().trim().toUpperCase());
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        
        List<Frame> frames = frameRepository.findDueFramesByUserAndDateOrderByStartTime(request.getUserId(), request.getDate());
        List<ConsumableOrder> consumableOrders = consumableOrderRepository.findByUserIdAndPaymentStatusAndCreatedDate(
                request.getUserId(), "UNPAID", request.getDate());

        BigDecimal totalFrameOutstanding = frameRepository.getTotalDueForUserByDate(request.getUserId(), request.getDate());
        if (totalFrameOutstanding == null) totalFrameOutstanding = BigDecimal.ZERO;
        
        BigDecimal totalConsumableOutstanding = consumableOrderRepository.getTotalUnpaidDueByUserIdAndDate(request.getUserId(), request.getDate());
        if (totalConsumableOutstanding == null) totalConsumableOutstanding = BigDecimal.ZERO;

        BigDecimal totalKidsOutstanding = kidsPlayService.getKidsDueByDate(request.getUserId(), request.getDate());
        if (totalKidsOutstanding == null) totalKidsOutstanding = BigDecimal.ZERO;

        BigDecimal totalOutstanding = totalFrameOutstanding.add(totalConsumableOutstanding).add(totalKidsOutstanding);
        BigDecimal totalSettlement = request.getPaidAmount().add(discount);

        if (totalSettlement.compareTo(totalOutstanding) > 0) {
            throw new IllegalArgumentException("Payment amount plus discount exceeds total due for the selected date");
        }

        AllocationState allocationState = new AllocationState(request.getPaidAmount(), discount);

        for (Frame frame : frames) {
            if (allocationState.isExhausted()) break;
            BigDecimal due = frame.getPaymentDue();
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
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            frame.setPaymentDue(updatedDue);
            frame.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
            frameRepository.save(frame);
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
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(settlementAmount);
            order.setTotalAmount(updatedDue);
            order.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            consumableOrderRepository.save(order);
        }

        if (!allocationState.isExhausted()) {
            for (com.youngstersclub.app.entity.KidsPlaySession session : kidsPlayService.getUnpaidSessionsByDate(request.getUserId(), request.getDate())) {
                if (allocationState.isExhausted()) break;
                BigDecimal due = session.getTotalAmount();
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
                paymentRepository.save(payment);

                BigDecimal updatedDue = due.subtract(settlementAmount);
                session.setTotalAmount(updatedDue);
                session.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "UNPAID");
            }
        }
    }

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
