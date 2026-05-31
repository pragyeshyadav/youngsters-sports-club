package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    interface SettledPaymentProjection {
        String getUserName();
        BigDecimal getPaidAmount();
        BigDecimal getDiscount();
        LocalDateTime getDate();
    }

    @Query("""
        SELECT
            p.user.name AS userName,
            p.amount AS paidAmount,
            p.discount AS discount,
            p.paymentTime AS date
        FROM Payment p
        WHERE p.status = com.youngstersclub.app.enums.PaymentStatus.PAID
          AND p.paymentTime IS NOT NULL
          AND FUNCTION('DATE', p.paymentTime) = :selectedDate
        ORDER BY p.paymentTime DESC, p.id DESC
    """)
    List<SettledPaymentProjection> findSettledPaymentsByDate(@Param("selectedDate") LocalDate selectedDate);
}
