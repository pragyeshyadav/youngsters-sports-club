package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.enums.FrameStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FrameRepository extends JpaRepository<Frame, Integer> {
    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
        AND f.endTime IS NULL
    """)
    List<Frame> findAllOngoingFrames();
    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.status = :status
        AND (
            f.startedBy.id = :userId
            OR EXISTS (
                SELECT 1 FROM FramePlayer participant
                WHERE participant.frame = f
                AND participant.user.id = :userId
            )
        )
    """)
    Optional<Frame> findActiveFrameForUser(@Param("userId") Integer userId, @Param("status") FrameStatus status);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.winner
        LEFT JOIN FETCH f.looser
        LEFT JOIN f.framePlayers fp
        WHERE f.startedBy.id = :userId
           OR fp.user.id = :userId
        ORDER BY f.startTime DESC
    """)
    List<Frame> findUserFrameHistory(@Param("userId") Integer userId);

    @Query("""
        SELECT COALESCE(SUM(f.paymentDue), 0)
        FROM Frame f
        WHERE f.looser.id = :userId
        AND f.paymentDue IS NOT NULL
        AND f.paymentDue > 0
    """)
    BigDecimal getTotalDueForUser(@Param("userId") Integer userId);

    @Query("""
        SELECT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        WHERE f.id = :frameId
    """)
    Optional<Frame> findDetailedById(@Param("frameId") Integer frameId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.startedBy
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
        AND f.endTime IS NULL
        AND f.startTime >= :startOfDay
        AND f.startTime < :endOfDay
        ORDER BY f.startTime DESC
    """)
    List<Frame> findTodayOngoingFrames(@Param("startOfDay") java.time.LocalDateTime startOfDay,
                                       @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.winner
        LEFT JOIN FETCH f.looser
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.ENDED
        AND f.endTime IS NOT NULL
        AND f.startTime >= :startOfDay
        AND f.startTime < :endOfDay
        ORDER BY f.startTime DESC
    """)
    List<Frame> findTodayCompletedFrames(@Param("startOfDay") java.time.LocalDateTime startOfDay,
                                         @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.winner
        LEFT JOIN FETCH f.looser
        LEFT JOIN f.framePlayers fp
        WHERE f.paymentDue IS NOT NULL
        AND f.paymentDue > 0
        AND (
            f.looser.id = :userId
            OR fp.user.id = :userId
        )
        ORDER BY f.startTime DESC
    """)
    List<Frame> findDueFramesByUser(@Param("userId") Integer userId);

    @Query("""
        SELECT f FROM Frame f
        WHERE f.looser.id = :userId
        AND f.paymentDue IS NOT NULL
        AND f.paymentDue > 0
        ORDER BY f.startTime ASC
    """)
    List<Frame> findDueFramesByUserOrderByStartTime(@Param("userId") Integer userId);

    @Query("""
        SELECT COALESCE(SUM(f.totalAmount), 0)
        FROM Frame f
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.ENDED
        AND f.endTime IS NOT NULL
        AND f.endTime >= :startDateTime
        AND f.endTime < :endDateTime
    """)
    BigDecimal getCompletedEarningsBetween(
            @Param("startDateTime") java.time.LocalDateTime startDateTime,
            @Param("endDateTime") java.time.LocalDateTime endDateTime);

    interface SnookerTableEarningsProjection {
        String getTableName();
        BigDecimal getTotal();
    }

    @Query(value = """
        SELECT
            st.table_name AS tableName,
            COALESCE(SUM(f.total_amount), 0) AS total
        FROM frames f
        JOIN snooker_tables st ON st.id = f.table_id
        WHERE f.status = 'ENDED'
          AND f.end_time IS NOT NULL
          AND f.end_time >= :startDateTime
          AND f.end_time < :endDateTime
        GROUP BY st.table_name
        ORDER BY total DESC, st.table_name ASC
    """, nativeQuery = true)
    List<SnookerTableEarningsProjection> getCompletedEarningsByTableBetween(
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    interface TopPlayerProjection {
        String getName();
        Long getWins();
    }

    interface TodayEarningsProjection {
        BigDecimal getTotalEarnings();
        BigDecimal getTotalDue();
        String getPlayerName();
        BigDecimal getDueAmount();
    }

    @Query(value = """
        SELECT 
            u.name AS name,
            COUNT(f.id) AS wins
        FROM frames f
        JOIN users u ON f.winner = u.id
        WHERE 
            f.winner IS NOT NULL
            AND DATE_TRUNC('month', f.start_time) = DATE_TRUNC('month', CURRENT_DATE)
        GROUP BY u.id, u.name
        ORDER BY wins DESC
        LIMIT 3
    """, nativeQuery = true)
    List<TopPlayerProjection> findTopPlayersOfCurrentMonth();

    @Query(value = """
        WITH today_frames AS (
            SELECT
                f.id,
                f.looser,
                f.status,
                COALESCE(f.total_amount, 0) AS total_amount,
                COALESCE(f.payment_due, 0) AS payment_due
            FROM frames f
            WHERE f.start_time >= CURRENT_DATE
              AND f.start_time < CURRENT_DATE + INTERVAL '1 day'
        ),
        frame_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(payment_due), 0) AS total_due
            FROM today_frames
        ),
        today_consumable_orders AS (
            SELECT
                co.id,
                co.payment_status,
                COALESCE(co.total_amount, 0) AS outstanding_due,
                COALESCE(SUM(coi.total_cost), 0) AS gross_amount
            FROM consumable_orders co
            LEFT JOIN consumable_order_items coi ON coi.order_id = co.id
            WHERE co.created_at >= CURRENT_DATE
              AND co.created_at < CURRENT_DATE + INTERVAL '1 day'
            GROUP BY co.id, co.payment_status, co.total_amount
        ),
        consumable_totals AS (
            SELECT
                COALESCE(SUM(gross_amount), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN outstanding_due > 0 AND payment_status <> 'PAID' THEN outstanding_due ELSE 0 END), 0) AS total_due
            FROM today_consumable_orders
        ),
        today_kids_sessions AS (
            SELECT
                k.status,
                k.payment_status,
                COALESCE(k.total_amount, 0) AS total_amount
            FROM kids_play_sessions k
            WHERE k.start_time >= CURRENT_DATE
              AND k.start_time < CURRENT_DATE + INTERVAL '1 day'
        ),
        kids_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN total_amount > 0 AND payment_status <> 'PAID' THEN total_amount ELSE 0 END), 0) AS total_due
            FROM today_kids_sessions
        ),
        totals AS (
            SELECT
                COALESCE(ft.total_earnings, 0) + COALESCE(ct.total_earnings, 0) + COALESCE(kt.total_earnings, 0) AS total_earnings,
                COALESCE(ft.total_due, 0) + COALESCE(ct.total_due, 0) + COALESCE(kt.total_due, 0) AS total_due
            FROM frame_totals ft
            CROSS JOIN consumable_totals ct
            CROSS JOIN kids_totals kt
        ),
        due_entries AS (
            SELECT
                tf.looser AS user_id,
                COALESCE(tf.payment_due, 0) AS due_amount
            FROM today_frames tf
            WHERE tf.looser IS NOT NULL
              AND tf.payment_due > 0

            UNION ALL

            SELECT
                co.user_id AS user_id,
                COALESCE(co.total_amount, 0) AS due_amount
            FROM consumable_orders co
            WHERE co.created_at >= CURRENT_DATE
              AND co.created_at < CURRENT_DATE + INTERVAL '1 day'
              AND COALESCE(co.total_amount, 0) > 0
              AND co.payment_status <> 'PAID'

            UNION ALL

            SELECT
                k.parent_user_id AS user_id,
                COALESCE(k.total_amount, 0) AS due_amount
            FROM kids_play_sessions k
            WHERE k.start_time >= CURRENT_DATE
              AND k.start_time < CURRENT_DATE + INTERVAL '1 day'
              AND COALESCE(k.total_amount, 0) > 0
              AND k.payment_status <> 'PAID'
        ),
        due_players AS (
            SELECT
                u.name AS player_name,
                SUM(de.due_amount) AS due_amount
            FROM due_entries de
            JOIN users u ON de.user_id = u.id
            GROUP BY u.id, u.name
        )
        SELECT
            t.total_earnings AS totalEarnings,
            t.total_due AS totalDue,
            dp.player_name AS playerName,
            dp.due_amount AS dueAmount
        FROM totals t
        LEFT JOIN due_players dp ON TRUE
        ORDER BY dp.due_amount DESC NULLS LAST
    """, nativeQuery = true)
    List<TodayEarningsProjection> findTodayEarningsAnalytics();
}
