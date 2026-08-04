package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.KidsPlaySession;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KidsPlaySessionRepository extends JpaRepository<KidsPlaySession, Long> {
    Optional<KidsPlaySession> findByIdAndBranch_Id(Long id, Long branchId);
    List<KidsPlaySession> findByBranch_Id(Long branchId);

    interface UserKidsDueProjection {
        Integer getUserId();
        BigDecimal getAmount();
    }

    @Query("""
        SELECT k FROM KidsPlaySession k
        JOIN FETCH k.child c
        WHERE k.parentUser.id = :parentUserId
        AND k.branch.id = :branchId
        AND k.endTime IS NULL
        ORDER BY k.startTime DESC
    """)
    List<KidsPlaySession> findActiveByParentUserIdAndBranchId(
            @Param("parentUserId") Integer parentUserId,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT k FROM KidsPlaySession k
        JOIN FETCH k.child c
        WHERE k.child.id = :childId
        AND k.endTime IS NULL
    """)
    Optional<KidsPlaySession> findActiveByChildId(@Param("childId") Long childId);

    @Query("""
        SELECT k FROM KidsPlaySession k
        JOIN FETCH k.child c
        JOIN FETCH k.parentUser p
        WHERE k.endTime IS NULL
        AND k.branch.id = :branchId
        ORDER BY k.startTime DESC
    """)
    List<KidsPlaySession> findAllActiveSessionsByBranchId(@Param("branchId") Long branchId);

    @Query("""
        SELECT k FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.branch.id = :branchId
        AND k.paymentStatus = 'UNPAID'
        ORDER BY k.startTime ASC
    """)
    List<KidsPlaySession> findUnpaidByParentUserIdAndBranchIdOrderByStartTime(
            @Param("parentUserId") Integer parentUserId,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT k FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
        ORDER BY k.startTime ASC
    """)
    List<KidsPlaySession> findUnpaidByParentUserIdOrderByStartTime(@Param("parentUserId") Integer parentUserId);

    @Query("""
        SELECT k FROM KidsPlaySession k
        JOIN FETCH k.child c
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
        AND k.endTime IS NOT NULL
        AND FUNCTION('DATE', k.endTime) = :selectedDate
        ORDER BY k.startTime ASC
    """)
    List<KidsPlaySession> findUnpaidByParentUserIdAndEndDateOrderByStartTime(
            @Param("parentUserId") Integer parentUserId,
            @Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT COALESCE(SUM(k.totalAmount), 0)
        FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
    """)
    BigDecimal getTotalUnpaidDueByParentUserId(@Param("parentUserId") Integer parentUserId);

    @Query("""
        SELECT COALESCE(SUM(k.totalAmount), 0)
        FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
        AND k.endTime IS NOT NULL
        AND FUNCTION('DATE', k.endTime) = :selectedDate
    """)
    BigDecimal getTotalUnpaidDueByParentUserIdAndDate(
            @Param("parentUserId") Integer parentUserId,
            @Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT
            k.parentUser.id AS userId,
            COALESCE(SUM(k.totalAmount), 0) AS amount
        FROM KidsPlaySession k
        WHERE k.parentUser.id IN :userIds
        AND k.paymentStatus = 'UNPAID'
        GROUP BY k.parentUser.id
    """)
    List<UserKidsDueProjection> getTotalUnpaidDueByParentUserIds(@Param("userIds") List<Integer> userIds);

    @Query("""
        SELECT
            k.parentUser.id AS userId,
            COALESCE(SUM(k.totalAmount), 0) AS amount
        FROM KidsPlaySession k
        WHERE k.parentUser.id IN :userIds
        AND k.branch.id = :branchId
        AND k.paymentStatus = 'UNPAID'
        GROUP BY k.parentUser.id
    """)
    List<UserKidsDueProjection> getTotalUnpaidDueByParentUserIdsAndBranchId(
            @Param("userIds") List<Integer> userIds,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT
            k.parentUser.id AS userId,
            COALESCE(SUM(k.totalAmount), 0) AS amount
        FROM KidsPlaySession k
        WHERE k.parentUser.id IN :userIds
        AND k.branch.organization.id = :organizationId
        AND k.paymentStatus = 'UNPAID'
        GROUP BY k.parentUser.id
    """)
    List<UserKidsDueProjection> getTotalUnpaidDueByParentUserIdsAndOrganizationId(
            @Param("userIds") List<Integer> userIds,
            @Param("organizationId") Long organizationId);

    @Query(value = """
        SELECT COALESCE(SUM(
            CASE
                WHEN k.total_amount IS NOT NULL AND k.total_amount > 0 THEN k.total_amount
                WHEN k.duration_minutes IS NOT NULL AND k.rate_per_minute IS NOT NULL
                    THEN k.rate_per_minute * k.duration_minutes
                ELSE 0
            END
        ), 0)
        FROM kids_play_sessions k
        WHERE k.payment_status = 'PAID'
          AND k.status = 'ENDED'
          AND k.end_time IS NOT NULL
          AND k.end_time >= :startDateTime
          AND k.end_time < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetween(
            @Param("startDateTime") java.time.LocalDateTime startDateTime,
            @Param("endDateTime") java.time.LocalDateTime endDateTime);

    @Query(value = """
        SELECT COALESCE(SUM(
            CASE
                WHEN k.total_amount IS NOT NULL AND k.total_amount > 0 THEN k.total_amount
                WHEN k.duration_minutes IS NOT NULL AND k.rate_per_minute IS NOT NULL
                    THEN k.rate_per_minute * k.duration_minutes
                ELSE 0
            END
        ), 0)
        FROM kids_play_sessions k
        WHERE k.branch_id = :branchId
          AND k.payment_status = 'PAID'
          AND k.status = 'ENDED'
          AND k.end_time IS NOT NULL
          AND k.end_time >= :startDateTime
          AND k.end_time < :endDateTime
    """, nativeQuery = true)
    BigDecimal getPaidEarningsBetweenAndBranchId(
            @Param("branchId") Long branchId,
            @Param("startDateTime") java.time.LocalDateTime startDateTime,
            @Param("endDateTime") java.time.LocalDateTime endDateTime);
}
