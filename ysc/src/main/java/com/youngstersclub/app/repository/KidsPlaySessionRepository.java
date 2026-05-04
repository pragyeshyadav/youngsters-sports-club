package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.KidsPlaySession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface KidsPlaySessionRepository extends JpaRepository<KidsPlaySession, Long> {

    @Query("""
        SELECT k FROM KidsPlaySession k
        JOIN FETCH k.child c
        WHERE k.parentUser.id = :parentUserId
        AND k.endTime IS NULL
        ORDER BY k.startTime DESC
    """)
    List<KidsPlaySession> findActiveByParentUserId(@Param("parentUserId") Integer parentUserId);

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
        ORDER BY k.startTime DESC
    """)
    List<KidsPlaySession> findAllActiveSessions();

    @Query("""
        SELECT k FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
        ORDER BY k.startTime ASC
    """)
    List<KidsPlaySession> findUnpaidByParentUserIdOrderByStartTime(@Param("parentUserId") Integer parentUserId);

    @Query("""
        SELECT COALESCE(SUM(k.totalAmount), 0)
        FROM KidsPlaySession k
        WHERE k.parentUser.id = :parentUserId
        AND k.paymentStatus = 'UNPAID'
    """)
    BigDecimal getTotalUnpaidDueByParentUserId(@Param("parentUserId") Integer parentUserId);

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
}
