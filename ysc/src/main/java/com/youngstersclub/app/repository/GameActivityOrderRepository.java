package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.GameActivityOrder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameActivityOrderRepository extends JpaRepository<GameActivityOrder, Long> {
    List<GameActivityOrder> findByBranch_Id(Long branchId);
    java.util.Optional<GameActivityOrder> findByIdAndBranch_Id(Long id, Long branchId);

    @Query("""
        SELECT gao FROM GameActivityOrder gao
        JOIN FETCH gao.game g
        WHERE gao.parentUser.id = :parentUserId
        AND gao.branch.id = :branchId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        ORDER BY gao.createdAt ASC, gao.id ASC
    """)
    List<GameActivityOrder> findUnpaidByParentUserIdAndBranchIdOrderByCreatedAt(
            @Param("parentUserId") Integer parentUserId,
            @Param("branchId") Long branchId);

    interface UserActivityDueProjection {
        Integer getUserId();
        BigDecimal getAmount();
    }

    @Query("""
        SELECT gao FROM GameActivityOrder gao
        JOIN FETCH gao.game g
        WHERE gao.parentUser.id = :parentUserId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        ORDER BY gao.createdAt ASC, gao.id ASC
    """)
    List<GameActivityOrder> findUnpaidByParentUserIdOrderByCreatedAt(@Param("parentUserId") Integer parentUserId);

    @Query("""
        SELECT COALESCE(SUM(gao.totalAmount), 0)
        FROM GameActivityOrder gao
        WHERE gao.parentUser.id = :parentUserId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
    """)
    BigDecimal getTotalUnpaidDueByParentUserId(@Param("parentUserId") Integer parentUserId);

    @Query("""
        SELECT gao.parentUser.id AS userId, COALESCE(SUM(gao.totalAmount), 0) AS amount
        FROM GameActivityOrder gao
        WHERE gao.parentUser.id IN :userIds
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        GROUP BY gao.parentUser.id
    """)
    List<UserActivityDueProjection> getTotalUnpaidDueByParentUserIds(@Param("userIds") List<Integer> userIds);

    @Query("""
        SELECT gao.parentUser.id AS userId, COALESCE(SUM(gao.totalAmount), 0) AS amount
        FROM GameActivityOrder gao
        WHERE gao.parentUser.id IN :userIds
        AND gao.branch.id = :branchId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        GROUP BY gao.parentUser.id
    """)
    List<UserActivityDueProjection> getTotalUnpaidDueByParentUserIdsAndBranchId(
            @Param("userIds") List<Integer> userIds,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT gao.parentUser.id AS userId, COALESCE(SUM(gao.totalAmount), 0) AS amount
        FROM GameActivityOrder gao
        WHERE gao.parentUser.id IN :userIds
        AND gao.branch.organization.id = :organizationId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        GROUP BY gao.parentUser.id
    """)
    List<UserActivityDueProjection> getTotalUnpaidDueByParentUserIdsAndOrganizationId(
            @Param("userIds") List<Integer> userIds,
            @Param("organizationId") Long organizationId);

    @Query(value = """
        SELECT COALESCE(SUM(
            COALESCE(gao.number_of_children, 1) * gao.duration_minutes * gao.rate_per_minute
        ), 0)
        FROM game_activity_orders gao
        WHERE COALESCE(gao.is_paid, false) = true
          AND gao.created_at >= :startDateTime
          AND gao.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query(value = """
        SELECT COALESCE(SUM(
            COALESCE(gao.number_of_children, 1) * gao.duration_minutes * gao.rate_per_minute
        ), 0)
        FROM game_activity_orders gao
        WHERE gao.branch_id = :branchId
          AND COALESCE(gao.is_paid, false) = true
          AND gao.created_at >= :startDateTime
          AND gao.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetweenAndBranchId(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("branchId") Long branchId);

    @Query(value = """
        SELECT COALESCE(SUM(
            COALESCE(gao.number_of_children, 1) * gao.duration_minutes * gao.rate_per_minute
        ), 0)
        FROM game_activity_orders gao
        WHERE gao.created_at >= :startDateTime
          AND gao.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getGrossEarningsBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query(value = """
        SELECT COALESCE(SUM(
            COALESCE(gao.number_of_children, 1) * gao.duration_minutes * gao.rate_per_minute
        ), 0)
        FROM game_activity_orders gao
        WHERE gao.branch_id = :branchId
          AND gao.created_at >= :startDateTime
          AND gao.created_at < :endDateTime
    """, nativeQuery = true)
    BigDecimal getGrossEarningsBetweenAndBranchId(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT gao.parentUser.id AS userId, COALESCE(SUM(gao.totalAmount), 0) AS amount
        FROM GameActivityOrder gao
        WHERE COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        AND gao.createdAt >= :startDateTime
        AND gao.createdAt < :endDateTime
        GROUP BY gao.parentUser.id
    """)
    List<UserActivityDueProjection> getUnpaidDueByUserForDate(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("""
        SELECT COALESCE(SUM(gao.totalAmount), 0)
        FROM GameActivityOrder gao
        WHERE COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        AND gao.createdAt >= :startDateTime
        AND gao.createdAt < :endDateTime
    """)
    BigDecimal getTotalUnpaidDueBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query("""
        SELECT gao.parentUser.id AS userId, COALESCE(SUM(gao.totalAmount), 0) AS amount
        FROM GameActivityOrder gao
        WHERE gao.branch.id = :branchId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        AND gao.createdAt >= :startDateTime
        AND gao.createdAt < :endDateTime
        GROUP BY gao.parentUser.id
    """)
    List<UserActivityDueProjection> getUnpaidDueByUserForDateAndBranchId(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT COALESCE(SUM(gao.totalAmount), 0)
        FROM GameActivityOrder gao
        WHERE gao.branch.id = :branchId
        AND COALESCE(gao.isPaid, false) = false
        AND gao.totalAmount > 0
        AND gao.createdAt >= :startDateTime
        AND gao.createdAt < :endDateTime
    """)
    BigDecimal getTotalUnpaidDueBetweenAndBranchId(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime,
            @Param("branchId") Long branchId);
}
