package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {
    List<Payment> findByBranch_Id(Long branchId);
    java.util.Optional<Payment> findByIdAndBranch_Id(Integer id, Long branchId);

    @EntityGraph(attributePaths = {"user"})
    List<Payment> findByBranch_IdAndReferenceDateBetweenOrderByPaymentTimeDescIdDesc(
            Long branchId,
            LocalDate from,
            LocalDate to);

    interface SettledPaymentProjection {
        String getUserName();
        BigDecimal getPaidAmount();
        BigDecimal getDiscount();
        LocalDateTime getDate();
        String getPaymentMethod();
    }

    @Query("""
        SELECT
            p.user.name AS userName,
            p.amount AS paidAmount,
            p.discount AS discount,
            p.paymentTime AS date,
            CAST(p.paymentMethod AS string) AS paymentMethod
        FROM Payment p
        WHERE p.status = com.youngstersclub.app.enums.PaymentStatus.PAID
          AND p.branch.id = :branchId
          AND p.referenceDate BETWEEN :fromDate AND :toDate
          AND p.paymentTime IS NOT NULL
        ORDER BY p.paymentTime DESC, p.id DESC
    """)
    List<SettledPaymentProjection> findSettledPaymentsByBranchAndReferenceDateBetween(
            @Param("branchId") Long branchId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate);
}
