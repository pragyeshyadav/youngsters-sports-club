package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.enums.FrameStatus;
import java.math.BigDecimal;
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
        totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(payment_due), 0) AS total_due
            FROM today_frames
        ),
        due_players AS (
            SELECT
                u.name AS player_name,
                SUM(tf.payment_due) AS due_amount
            FROM today_frames tf
            JOIN users u ON tf.looser = u.id
            WHERE tf.payment_due > 0
            GROUP BY u.name
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
