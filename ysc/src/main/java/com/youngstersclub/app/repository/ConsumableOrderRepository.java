package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.ConsumableOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumableOrderRepository extends JpaRepository<ConsumableOrder, Long> {
    List<ConsumableOrder> findByBranch_Id(Long branchId);
    java.util.Optional<ConsumableOrder> findByIdAndBranch_Id(Long id, Long branchId);
    List<ConsumableOrder> findByUser_IdAndBranch_IdAndPaymentStatusOrderByCreatedAtAsc(Integer userId, Long branchId, String paymentStatus);

    interface UserConsumableDueProjection {
        Integer getUserId();
        BigDecimal getAmount();
    }

    @Query("""
        SELECT co FROM ConsumableOrder co
        WHERE co.user.id = :userId
        AND co.paymentStatus = :paymentStatus
        ORDER BY co.createdAt ASC
    """)
    List<ConsumableOrder> findByUserIdAndPaymentStatus(@Param("userId") Integer userId, @Param("paymentStatus") String paymentStatus);

    @Query("""
        SELECT DISTINCT co FROM ConsumableOrder co
        LEFT JOIN FETCH co.items coi
        LEFT JOIN FETCH coi.item
        WHERE co.user.id = :userId
        AND co.paymentStatus = :paymentStatus
        AND FUNCTION('DATE', co.createdAt) = :selectedDate
        ORDER BY co.createdAt ASC
    """)
    List<ConsumableOrder> findByUserIdAndPaymentStatusAndCreatedDate(
            @Param("userId") Integer userId,
            @Param("paymentStatus") String paymentStatus,
            @Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT COALESCE(SUM(co.totalAmount), 0)
        FROM ConsumableOrder co
        WHERE co.user.id = :userId
        AND co.paymentStatus = 'UNPAID'
    """)
    BigDecimal getTotalUnpaidDueByUserId(@Param("userId") Integer userId);

    @Query("""
        SELECT COALESCE(SUM(co.totalAmount), 0)
        FROM ConsumableOrder co
        WHERE co.user.id = :userId
        AND co.paymentStatus = 'UNPAID'
        AND FUNCTION('DATE', co.createdAt) = :selectedDate
    """)
    BigDecimal getTotalUnpaidDueByUserIdAndDate(
            @Param("userId") Integer userId,
            @Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT
            co.user.id AS userId,
            COALESCE(SUM(co.totalAmount), 0) AS amount
        FROM ConsumableOrder co
        WHERE co.user.id IN :userIds
        AND co.paymentStatus = 'UNPAID'
        GROUP BY co.user.id
    """)
    List<UserConsumableDueProjection> getTotalUnpaidDueByUserIds(@Param("userIds") List<Integer> userIds);

    @Query("""
        SELECT
            co.user.id AS userId,
            COALESCE(SUM(co.totalAmount), 0) AS amount
        FROM ConsumableOrder co
        WHERE co.user.id IN :userIds
        AND co.branch.id = :branchId
        AND co.paymentStatus = 'UNPAID'
        GROUP BY co.user.id
    """)
    List<UserConsumableDueProjection> getTotalUnpaidDueByUserIdsAndBranchId(
            @Param("userIds") List<Integer> userIds,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT
            co.user.id AS userId,
            COALESCE(SUM(co.totalAmount), 0) AS amount
        FROM ConsumableOrder co
        WHERE co.user.id IN :userIds
        AND co.branch.organization.id = :organizationId
        AND co.paymentStatus = 'UNPAID'
        GROUP BY co.user.id
    """)
    List<UserConsumableDueProjection> getTotalUnpaidDueByUserIdsAndOrganizationId(
            @Param("userIds") List<Integer> userIds,
            @Param("organizationId") Long organizationId);

    @Query(value = """
        SELECT COALESCE(SUM(coi.total_cost), 0)
        FROM consumable_orders co
        JOIN consumable_order_items coi ON coi.order_id = co.id
        WHERE co.payment_status = 'PAID'
          AND co.created_at >= :startDateTime
          AND co.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetween(
            @Param("startDateTime") java.time.LocalDateTime startDateTime,
            @Param("endDateTime") java.time.LocalDateTime endDateTime);

    @Query(value = """
        SELECT COALESCE(SUM(coi.total_cost), 0)
        FROM consumable_orders co
        JOIN consumable_order_items coi ON coi.order_id = co.id
        WHERE co.branch_id = :branchId
          AND co.payment_status = 'PAID'
          AND co.created_at >= :startDateTime
          AND co.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetweenAndBranchId(
            @Param("branchId") Long branchId,
            @Param("startDateTime") java.time.LocalDateTime startDateTime,
            @Param("endDateTime") java.time.LocalDateTime endDateTime);

    interface DueOrderItemProjection {
        Long getOrderId();
        String getItemName();
        Integer getQuantity();
        BigDecimal getPrice();
        BigDecimal getTotalCost();
        LocalDateTime getCreatedAt();
    }

    @Query("""
        SELECT
            co.id AS orderId,
            coi.item.name AS itemName,
            coi.quantity AS quantity,
            coi.price AS price,
            coi.totalCost AS totalCost,
            co.createdAt AS createdAt
        FROM ConsumableOrder co
        JOIN co.items coi
        WHERE co.user.id = :userId
        AND co.paymentStatus = 'UNPAID'
        ORDER BY co.createdAt ASC, coi.id ASC
    """)
    List<DueOrderItemProjection> findUnpaidOrderItemsByUserId(@Param("userId") Integer userId);

    @Query("""
        SELECT
            co.id AS orderId,
            coi.item.name AS itemName,
            coi.quantity AS quantity,
            coi.price AS price,
            coi.totalCost AS totalCost,
            co.createdAt AS createdAt
        FROM ConsumableOrder co
        JOIN co.items coi
        WHERE co.user.id = :userId
        AND co.branch.id = :branchId
        AND co.paymentStatus = 'UNPAID'
        ORDER BY co.createdAt ASC, coi.id ASC
    """)
    List<DueOrderItemProjection> findUnpaidOrderItemsByUserIdAndBranchId(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId);

    interface ConsumableHistoryProjection {
        String getItemName();
        Integer getQuantity();
        LocalDateTime getDate();
        BigDecimal getAmount();
        String getPaymentStatus();
    }

    @Query("""
        SELECT
            coi.item.name AS itemName,
            coi.quantity AS quantity,
            co.createdAt AS date,
            coi.totalCost AS amount,
            co.paymentStatus AS paymentStatus
        FROM ConsumableOrderItem coi
        JOIN coi.order co
        WHERE co.user.id = :userId
        ORDER BY co.createdAt DESC, coi.id DESC
    """)
    List<ConsumableHistoryProjection> findConsumableHistoryByUserId(@Param("userId") Integer userId);

    @Query("""
        SELECT
            coi.item.name AS itemName,
            coi.quantity AS quantity,
            co.createdAt AS date,
            coi.totalCost AS amount,
            co.paymentStatus AS paymentStatus
        FROM ConsumableOrderItem coi
        JOIN coi.order co
        WHERE co.user.id = :userId
        AND co.branch.id = :branchId
        ORDER BY co.createdAt DESC, coi.id DESC
    """)
    List<ConsumableHistoryProjection> findConsumableHistoryByUserIdAndBranchId(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId);
}
