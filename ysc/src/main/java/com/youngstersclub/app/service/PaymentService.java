package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PaymentRequest;
import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.entity.Payment;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.PaymentMethod;
import com.youngstersclub.app.enums.PaymentStatus;
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
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public PaymentService(
            FrameRepository frameRepository,
            PaymentRepository paymentRepository,
            UserRepository userRepository) {
        this.frameRepository = frameRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
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

        PaymentMethod paymentMethod = PaymentMethod.valueOf(request.getMode().trim().toUpperCase());
        User user = userRepository.findById(request.getUserId()).orElseThrow();
        List<Frame> frames = frameRepository.findDueFramesByUserOrderByStartTime(request.getUserId());

        BigDecimal totalOutstanding = frames.stream()
                .map(Frame::getPaymentDue)
                .filter(due -> due != null && due.compareTo(BigDecimal.ZERO) > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (request.getAmount().compareTo(totalOutstanding) > 0) {
            throw new IllegalArgumentException("Payment amount exceeds total due");
        }

        BigDecimal remaining = request.getAmount();

        for (Frame frame : frames) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal due = frame.getPaymentDue();
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal paymentAmount = remaining.min(due);

            Payment payment = new Payment();
            payment.setFrame(frame);
            payment.setUser(user);
            payment.setAmount(paymentAmount);
            payment.setStatus(PaymentStatus.PAID);
            payment.setPaymentMethod(paymentMethod);
            payment.setPaymentTime(TimeUtil.nowIST());
            paymentRepository.save(payment);

            BigDecimal updatedDue = due.subtract(paymentAmount);
            frame.setPaymentDue(updatedDue);
            frame.setPaymentStatus(updatedDue.compareTo(BigDecimal.ZERO) == 0
                    ? PaymentStatus.PAID
                    : PaymentStatus.PARTIAL);
            frameRepository.save(frame);

            remaining = remaining.subtract(paymentAmount);
        }
    }
}
