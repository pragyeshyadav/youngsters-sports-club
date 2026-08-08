package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Frame;
import com.youngstersclub.app.enums.FrameStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FrameRepository extends JpaRepository<Frame, Integer> {
    Optional<Frame> findByIdAndBranch_Id(Integer id, Long branchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT f FROM Frame f
        WHERE f.id = :frameId
        AND f.branch.id = :branchId
    """)
    Optional<Frame> findForUpdateByIdAndBranchId(@Param("frameId") Integer frameId, @Param("branchId") Long branchId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.branch.id = :branchId
        AND f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
        AND f.endTime IS NULL
    """)
    List<Frame> findAllOngoingFramesByBranchId(@Param("branchId") Long branchId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
        AND f.endTime IS NULL
    """)
    List<Frame> findAllOngoingFrames();

    interface TableStatusRowProjection {
        Long getTableId();
        String getTableName();
        Boolean getIsAvailable();
        Integer getActiveFrameId();
        String getPlayerName();
    }

    @Query(value = """
        SELECT
            st.id AS tableId,
            st.table_name AS tableName,
            st.is_available AS isAvailable,
            f.id AS activeFrameId,
            COALESCE(u.name, fp.player_name) AS playerName
        FROM snooker_tables st
        LEFT JOIN frames f
            ON f.table_id = st.id
           AND f.status = 'STARTED'
           AND f.end_time IS NULL
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users u
            ON u.id = fp.user_id
        ORDER BY st.id ASC, fp.id ASC
    """, nativeQuery = true)
    List<TableStatusRowProjection> findAllTableStatusRows();

    interface OngoingFrameRowProjection {
        Integer getFrameId();
        Long getTableId();
        String getTableName();
        LocalDateTime getStartTime();
        String getStatus();
        String getStartedByName();
        String getPlayerName();
    }
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

    interface UserFrameHistoryRowProjection {
        Integer getFrameId();
        LocalDateTime getStartTime();
        LocalDateTime getEndTime();
        Integer getDuration();
        BigDecimal getAmount();
        BigDecimal getPaymentDue();
        String getWinnerName();
        String getLooserName();
    }

    @Query(value = """
        SELECT
            f.id AS frameId,
            f.start_time AS startTime,
            f.end_time AS endTime,
            f.duration_minutes AS duration,
            f.total_amount AS amount,
            f.payment_due AS paymentDue,
            winner_user.name AS winnerName,
            loser_user.name AS looserName
        FROM frames f
        LEFT JOIN users winner_user
            ON winner_user.id = f.winner
        LEFT JOIN users loser_user
            ON loser_user.id = f.looser
        WHERE f.started_by = :userId
           OR EXISTS (
                SELECT 1
                FROM frame_players fp
                WHERE fp.frame_id = f.id
                  AND fp.user_id = :userId
           )
        ORDER BY f.start_time DESC, f.id DESC
    """, nativeQuery = true)
    List<UserFrameHistoryRowProjection> findUserFrameHistoryRows(@Param("userId") Integer userId);

    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN fp.id IS NOT NULL AND fp.amountDue IS NOT NULL THEN fp.amountDue
                ELSE f.paymentDue
            END
        ), 0)
        FROM Frame f
        LEFT JOIN f.framePlayers fp ON fp.user.id = :userId AND fp.amountDue IS NOT NULL
        WHERE (f.looser.id = :userId AND (fp.id IS NULL OR fp.amountDue IS NULL) AND f.paymentDue > 0)
           OR (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
    """)
    BigDecimal getTotalDueForUser(@Param("userId") Integer userId);

    @Query("""
        SELECT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.id = :frameId
    """)
    Optional<Frame> findDetailedById(@Param("frameId") Integer frameId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.id = :frameId
        AND f.branch.id = :branchId
    """)
    Optional<Frame> findDetailedByIdAndBranchId(@Param("frameId") Integer frameId, @Param("branchId") Long branchId);

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
        LEFT JOIN FETCH f.snookerTable
        LEFT JOIN FETCH f.startedBy
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.branch.id = :branchId
        AND f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
        AND f.endTime IS NULL
        AND f.startTime >= :startOfDay
        AND f.startTime < :endOfDay
        ORDER BY f.startTime DESC
    """)
    List<Frame> findTodayOngoingFramesByBranchId(
            @Param("branchId") Long branchId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
            @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query(value = """
        SELECT
            f.id AS frameId,
            st.id AS tableId,
            st.table_name AS tableName,
            f.start_time AS startTime,
            f.status AS status,
            starter.name AS startedByName,
            COALESCE(u.name, fp.player_name) AS playerName
        FROM frames f
        LEFT JOIN snooker_tables st
            ON st.id = f.table_id
        LEFT JOIN users starter
            ON starter.id = f.started_by
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users u
            ON u.id = fp.user_id
        WHERE f.branch_id = :branchId
          AND f.status = 'STARTED'
          AND f.end_time IS NULL
          AND f.start_time >= :startOfDay
          AND f.start_time < :endOfDay
        ORDER BY f.start_time DESC, f.id DESC, fp.id ASC
    """, nativeQuery = true)
    List<OngoingFrameRowProjection> findTodayOngoingFrameRowsByBranchId(
            @Param("branchId") Long branchId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
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
        LEFT JOIN FETCH f.snookerTable
        WHERE f.branch.id = :branchId
        AND f.status = com.youngstersclub.app.enums.FrameStatus.ENDED
        AND f.endTime IS NOT NULL
        AND f.startTime >= :startOfDay
        AND f.startTime < :endOfDay
        ORDER BY f.startTime DESC
    """)
    List<Frame> findTodayCompletedFramesByBranchId(
            @Param("branchId") Long branchId,
            @Param("startOfDay") java.time.LocalDateTime startOfDay,
            @Param("endOfDay") java.time.LocalDateTime endOfDay);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.winner
        LEFT JOIN FETCH f.looser
        LEFT JOIN FETCH f.framePlayers fp
        WHERE (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime DESC
    """)
    List<Frame> findDueFramesByUser(@Param("userId") Integer userId);

    interface DueFrameRowProjection {
        Integer getFrameId();
        LocalDateTime getStartTime();
        LocalDateTime getEndTime();
        Integer getDuration();
        BigDecimal getAmount();
        BigDecimal getPaymentDue();
        String getWinnerName();
        String getLooserName();
        String getPlayerName();
        Boolean getIsWinner();
        Boolean getIsLoser();
        BigDecimal getUserAmountDue();
    }

    @Query(value = """
        SELECT
            f.id AS frameId,
            f.start_time AS startTime,
            f.end_time AS endTime,
            f.duration_minutes AS duration,
            f.total_amount AS amount,
            f.payment_due AS paymentDue,
            winner_user.name AS winnerName,
            loser_user.name AS looserName,
            COALESCE(player_user.name, fp.player_name) AS playerName,
            fp.is_winner AS isWinner,
            fp.is_loser AS isLoser,
            CASE
                WHEN fp.user_id = :userId
                     AND fp.amount_due IS NOT NULL
                     AND fp.amount_due > 0
                THEN fp.amount_due
                ELSE NULL
            END AS userAmountDue
        FROM frames f
        LEFT JOIN users winner_user
            ON winner_user.id = f.winner
        LEFT JOIN users loser_user
            ON loser_user.id = f.looser
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users player_user
            ON player_user.id = fp.user_id
        WHERE (
            (f.looser = :userId
                AND f.payment_due IS NOT NULL
                AND f.payment_due > 0
                AND NOT EXISTS (
                    SELECT 1
                    FROM frame_players fpi
                    WHERE fpi.frame_id = f.id
                      AND fpi.amount_due IS NOT NULL
                ))
            OR EXISTS (
                SELECT 1
                FROM frame_players due_fp
                WHERE due_fp.frame_id = f.id
                  AND due_fp.user_id = :userId
                  AND due_fp.amount_due IS NOT NULL
                  AND due_fp.amount_due > 0
            )
        )
        ORDER BY f.start_time DESC, f.id DESC, fp.id ASC
    """, nativeQuery = true)
    List<DueFrameRowProjection> findDueFrameRowsByUser(@Param("userId") Integer userId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.framePlayers fp
        WHERE (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime ASC
    """)
    List<Frame> findDueFramesByUserOrderByStartTime(@Param("userId") Integer userId);

    @Query(value = """
        SELECT
            f.id AS frameId,
            f.start_time AS startTime,
            f.end_time AS endTime,
            f.duration_minutes AS duration,
            f.total_amount AS amount,
            f.payment_due AS paymentDue,
            winner_user.name AS winnerName,
            loser_user.name AS looserName,
            COALESCE(player_user.name, fp.player_name) AS playerName,
            fp.is_winner AS isWinner,
            fp.is_loser AS isLoser,
            CASE
                WHEN fp.user_id = :userId
                     AND fp.amount_due IS NOT NULL
                     AND fp.amount_due > 0
                THEN fp.amount_due
                ELSE NULL
            END AS userAmountDue
        FROM frames f
        LEFT JOIN users winner_user
            ON winner_user.id = f.winner
        LEFT JOIN users loser_user
            ON loser_user.id = f.looser
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users player_user
            ON player_user.id = fp.user_id
        WHERE (
            (f.looser = :userId
                AND f.payment_due IS NOT NULL
                AND f.payment_due > 0
                AND NOT EXISTS (
                    SELECT 1
                    FROM frame_players fpi
                    WHERE fpi.frame_id = f.id
                      AND fpi.amount_due IS NOT NULL
                ))
            OR EXISTS (
                SELECT 1
                FROM frame_players due_fp
                WHERE due_fp.frame_id = f.id
                  AND due_fp.user_id = :userId
                  AND due_fp.amount_due IS NOT NULL
                  AND due_fp.amount_due > 0
            )
        )
          AND f.start_time >= :startOfDay
          AND f.start_time < :endOfDay
        ORDER BY f.start_time ASC, f.id ASC, fp.id ASC
    """, nativeQuery = true)
    List<DueFrameRowProjection> findDueFrameRowsByUserAndStartTimeBetween(
            @Param("userId") Integer userId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.winner
        LEFT JOIN FETCH f.looser
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.branch.id = :branchId
        AND (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime DESC
    """)
    List<Frame> findDueFramesByUserAndBranch(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId);

    @Query(value = """
        SELECT
            f.id AS frameId,
            f.start_time AS startTime,
            f.end_time AS endTime,
            f.duration_minutes AS duration,
            f.total_amount AS amount,
            f.payment_due AS paymentDue,
            winner_user.name AS winnerName,
            loser_user.name AS looserName,
            COALESCE(player_user.name, fp.player_name) AS playerName,
            fp.is_winner AS isWinner,
            fp.is_loser AS isLoser,
            CASE
                WHEN fp.user_id = :userId
                     AND fp.amount_due IS NOT NULL
                     AND fp.amount_due > 0
                THEN fp.amount_due
                ELSE NULL
            END AS userAmountDue
        FROM frames f
        LEFT JOIN users winner_user
            ON winner_user.id = f.winner
        LEFT JOIN users loser_user
            ON loser_user.id = f.looser
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users player_user
            ON player_user.id = fp.user_id
        WHERE f.branch_id = :branchId
          AND (
            (f.looser = :userId
                AND f.payment_due IS NOT NULL
                AND f.payment_due > 0
                AND NOT EXISTS (
                    SELECT 1
                    FROM frame_players fpi
                    WHERE fpi.frame_id = f.id
                      AND fpi.amount_due IS NOT NULL
                ))
            OR EXISTS (
                SELECT 1
                FROM frame_players due_fp
                WHERE due_fp.frame_id = f.id
                  AND due_fp.user_id = :userId
                  AND due_fp.amount_due IS NOT NULL
                  AND due_fp.amount_due > 0
            )
        )
        ORDER BY f.start_time DESC, f.id DESC, fp.id ASC
    """, nativeQuery = true)
    List<DueFrameRowProjection> findDueFrameRowsByUserAndBranch(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.branch.id = :branchId
        AND (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime ASC
    """)
    List<Frame> findDueFramesByUserAndBranchOrderByStartTime(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId);

    @Query(value = """
        SELECT
            f.id AS frameId,
            f.start_time AS startTime,
            f.end_time AS endTime,
            f.duration_minutes AS duration,
            f.total_amount AS amount,
            f.payment_due AS paymentDue,
            winner_user.name AS winnerName,
            loser_user.name AS looserName,
            COALESCE(player_user.name, fp.player_name) AS playerName,
            fp.is_winner AS isWinner,
            fp.is_loser AS isLoser,
            CASE
                WHEN fp.user_id = :userId
                     AND fp.amount_due IS NOT NULL
                     AND fp.amount_due > 0
                THEN fp.amount_due
                ELSE NULL
            END AS userAmountDue
        FROM frames f
        LEFT JOIN users winner_user
            ON winner_user.id = f.winner
        LEFT JOIN users loser_user
            ON loser_user.id = f.looser
        LEFT JOIN frame_players fp
            ON fp.frame_id = f.id
        LEFT JOIN users player_user
            ON player_user.id = fp.user_id
        WHERE f.branch_id = :branchId
          AND (
            (f.looser = :userId
                AND f.payment_due IS NOT NULL
                AND f.payment_due > 0
                AND NOT EXISTS (
                    SELECT 1
                    FROM frame_players fpi
                    WHERE fpi.frame_id = f.id
                      AND fpi.amount_due IS NOT NULL
                ))
            OR EXISTS (
                SELECT 1
                FROM frame_players due_fp
                WHERE due_fp.frame_id = f.id
                  AND due_fp.user_id = :userId
                  AND due_fp.amount_due IS NOT NULL
                  AND due_fp.amount_due > 0
            )
        )
          AND f.start_time >= :startOfDay
          AND f.start_time < :endOfDay
        ORDER BY f.start_time ASC, f.id ASC, fp.id ASC
    """, nativeQuery = true)
    List<DueFrameRowProjection> findDueFrameRowsByUserAndBranchAndStartTimeBetween(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.framePlayers fp
        LEFT JOIN FETCH fp.user
        WHERE f.branch.id = :branchId
        AND f.startTime >= :startOfDay
        AND f.startTime < :endOfDay
        AND (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime ASC
    """)
    List<Frame> findSettlementDueFramesByUserAndBranchAndStartTimeBetweenOrderByStartTime(
            @Param("userId") Integer userId,
            @Param("branchId") Long branchId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay);

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
        Integer getUserId();
        String getName();
        Long getWins();
    }

    interface UserDueProjection {
        Integer getUserId();
        BigDecimal getAmount();
    }

    interface TodayEarningsProjection {
        Integer getUserId();
        BigDecimal getTotalEarnings();
        BigDecimal getTotalDue();
        String getPlayerName();
        BigDecimal getDueAmount();
    }

    @Query(value = """
        SELECT
            u.id AS userId,
            u.name AS name,
            COUNT(f.id) AS wins
        FROM frames f
        JOIN users u ON f.winner = u.id
        WHERE f.branch_id = :branchId
          AND f.status = 'ENDED'
          AND f.winner IS NOT NULL
          AND f.end_time IS NOT NULL
          AND f.end_time >= :startInclusive
          AND f.end_time < :endExclusive
        GROUP BY u.id, u.name
        ORDER BY wins DESC, u.name ASC, u.id ASC
        LIMIT 10
    """, nativeQuery = true)
    List<TopPlayerProjection> findTopPlayersOfMonthByBranch(
            @Param("branchId") Long branchId,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
        SELECT
            due.user_id AS userId,
            COALESCE(SUM(due.amount), 0) AS amount
        FROM (
            SELECT
                f.looser AS user_id,
                COALESCE(f.payment_due, 0) AS amount
            FROM frames f
            WHERE f.looser IS NOT NULL
              AND COALESCE(f.payment_due, 0) > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM frame_players fp_check
                  WHERE fp_check.frame_id = f.id
                    AND fp_check.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS amount
            FROM frame_players fp
            WHERE fp.user_id IS NOT NULL
              AND COALESCE(fp.amount_due, 0) > 0
        ) due
        WHERE due.user_id IN (:userIds)
        GROUP BY due.user_id
    """, nativeQuery = true)
    List<UserDueProjection> getTotalDueForUsers(@Param("userIds") List<Integer> userIds);

    @Query(value = """
        SELECT
            due.user_id AS userId,
            COALESCE(SUM(due.amount), 0) AS amount
        FROM (
            SELECT
                f.looser AS user_id,
                COALESCE(f.payment_due, 0) AS amount
            FROM frames f
            WHERE f.branch_id = :branchId
              AND f.looser IS NOT NULL
              AND COALESCE(f.payment_due, 0) > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM frame_players fp_check
                  WHERE fp_check.frame_id = f.id
                    AND fp_check.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS amount
            FROM frame_players fp
            JOIN frames f ON f.id = fp.frame_id
            WHERE f.branch_id = :branchId
              AND fp.user_id IS NOT NULL
              AND COALESCE(fp.amount_due, 0) > 0
        ) due
        WHERE due.user_id IN (:userIds)
        GROUP BY due.user_id
    """, nativeQuery = true)
    List<UserDueProjection> getTotalDueForUsersByBranch(
            @Param("userIds") List<Integer> userIds,
            @Param("branchId") Long branchId);

    @Query(value = """
        SELECT
            due.user_id AS userId,
            COALESCE(SUM(due.amount), 0) AS amount
        FROM (
            SELECT
                f.looser AS user_id,
                COALESCE(f.payment_due, 0) AS amount
            FROM frames f
            JOIN branches b ON b.id = f.branch_id
            WHERE b.organization_id = :organizationId
              AND f.looser IS NOT NULL
              AND COALESCE(f.payment_due, 0) > 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM frame_players fp_check
                  WHERE fp_check.frame_id = f.id
                    AND fp_check.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS amount
            FROM frame_players fp
            JOIN frames f ON f.id = fp.frame_id
            JOIN branches b ON b.id = f.branch_id
            WHERE b.organization_id = :organizationId
              AND fp.user_id IS NOT NULL
              AND COALESCE(fp.amount_due, 0) > 0
        ) due
        WHERE due.user_id IN (:userIds)
        GROUP BY due.user_id
    """, nativeQuery = true)
    List<UserDueProjection> getTotalDueForUsersByOrganization(
            @Param("userIds") List<Integer> userIds,
            @Param("organizationId") Long organizationId);

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
              AND NOT EXISTS (
                  SELECT 1 FROM frame_players fp WHERE fp.frame_id = tf.id AND fp.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS due_amount
            FROM frame_players fp
            JOIN today_frames tf ON fp.frame_id = tf.id
            WHERE fp.user_id IS NOT NULL
              AND fp.amount_due > 0

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
                u.id AS user_id,
                u.name AS player_name,
                SUM(de.due_amount) AS due_amount
            FROM due_entries de
            JOIN users u ON de.user_id = u.id
            GROUP BY u.id, u.name
        )
        SELECT
            dp.user_id AS userId,
            t.total_earnings AS totalEarnings,
            t.total_due AS totalDue,
            dp.player_name AS playerName,
            dp.due_amount AS dueAmount
        FROM totals t
        LEFT JOIN due_players dp ON TRUE
        ORDER BY dp.due_amount DESC NULLS LAST
    """, nativeQuery = true)
    List<TodayEarningsProjection> findTodayEarningsAnalytics();

    @Query(value = """
        WITH selected_frames AS (
            SELECT
                f.id,
                f.looser,
                f.status,
                COALESCE(f.total_amount, 0) AS total_amount,
                COALESCE(f.payment_due, 0) AS payment_due
            FROM frames f
            WHERE f.branch_id = :branchId
              AND f.start_time >= :startDateTime
              AND f.start_time < :endDateTime
        ),
        frame_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(payment_due), 0) AS total_due
            FROM selected_frames
        ),
        selected_consumable_orders AS (
            SELECT
                co.id,
                co.payment_status,
                COALESCE(co.total_amount, 0) AS outstanding_due,
                COALESCE(SUM(coi.total_cost), 0) AS gross_amount
            FROM consumable_orders co
            LEFT JOIN consumable_order_items coi ON coi.order_id = co.id
            WHERE co.branch_id = :branchId
              AND co.created_at >= :startDateTime
              AND co.created_at < :endDateTime
            GROUP BY co.id, co.payment_status, co.total_amount
        ),
        consumable_totals AS (
            SELECT
                COALESCE(SUM(gross_amount), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN outstanding_due > 0 AND payment_status <> 'PAID' THEN outstanding_due ELSE 0 END), 0) AS total_due
            FROM selected_consumable_orders
        ),
        selected_kids_sessions AS (
            SELECT
                k.status,
                k.payment_status,
                COALESCE(k.total_amount, 0) AS total_amount
            FROM kids_play_sessions k
            WHERE k.branch_id = :branchId
              AND k.start_time >= :startDateTime
              AND k.start_time < :endDateTime
        ),
        kids_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN total_amount > 0 AND payment_status <> 'PAID' THEN total_amount ELSE 0 END), 0) AS total_due
            FROM selected_kids_sessions
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
                sf.looser AS user_id,
                COALESCE(sf.payment_due, 0) AS due_amount
            FROM selected_frames sf
            WHERE sf.looser IS NOT NULL
              AND sf.payment_due > 0
              AND NOT EXISTS (
                  SELECT 1 FROM frame_players fp WHERE fp.frame_id = sf.id AND fp.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS due_amount
            FROM frame_players fp
            JOIN selected_frames sf ON fp.frame_id = sf.id
            WHERE fp.user_id IS NOT NULL
              AND fp.amount_due > 0

            UNION ALL

            SELECT
                co.user_id AS user_id,
                COALESCE(co.total_amount, 0) AS due_amount
            FROM consumable_orders co
            WHERE co.branch_id = :branchId
              AND co.created_at >= :startDateTime
              AND co.created_at < :endDateTime
              AND COALESCE(co.total_amount, 0) > 0
              AND co.payment_status <> 'PAID'

            UNION ALL

            SELECT
                k.parent_user_id AS user_id,
                COALESCE(k.total_amount, 0) AS due_amount
            FROM kids_play_sessions k
            WHERE k.branch_id = :branchId
              AND k.start_time >= :startDateTime
              AND k.start_time < :endDateTime
              AND COALESCE(k.total_amount, 0) > 0
              AND k.payment_status <> 'PAID'
        ),
        due_players AS (
            SELECT
                u.id AS user_id,
                u.name AS player_name,
                SUM(de.due_amount) AS due_amount
            FROM due_entries de
            JOIN users u ON de.user_id = u.id
            GROUP BY u.id, u.name
        )
        SELECT
            dp.user_id AS userId,
            t.total_earnings AS totalEarnings,
            t.total_due AS totalDue,
            dp.player_name AS playerName,
            dp.due_amount AS dueAmount
        FROM totals t
        LEFT JOIN due_players dp ON TRUE
        ORDER BY dp.due_amount DESC NULLS LAST
    """, nativeQuery = true)
    List<TodayEarningsProjection> findEarningsAnalyticsByBranchAndDateRange(
            @Param("branchId") Long branchId,
            @Param("startDateTime") LocalDateTime startDateTime,
            @Param("endDateTime") LocalDateTime endDateTime);

    @Query(value = """
        WITH selected_frames AS (
            SELECT
                f.id,
                f.looser,
                f.status,
                COALESCE(f.total_amount, 0) AS total_amount,
                COALESCE(f.payment_due, 0) AS payment_due
            FROM frames f
            WHERE DATE(f.start_time) = :selectedDate
        ),
        frame_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(payment_due), 0) AS total_due
            FROM selected_frames
        ),
        selected_consumable_orders AS (
            SELECT
                co.id,
                co.payment_status,
                COALESCE(co.total_amount, 0) AS outstanding_due,
                COALESCE(SUM(coi.total_cost), 0) AS gross_amount
            FROM consumable_orders co
            LEFT JOIN consumable_order_items coi ON coi.order_id = co.id
            WHERE DATE(co.created_at) = :selectedDate
            GROUP BY co.id, co.payment_status, co.total_amount
        ),
        consumable_totals AS (
            SELECT
                COALESCE(SUM(gross_amount), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN outstanding_due > 0 AND payment_status <> 'PAID' THEN outstanding_due ELSE 0 END), 0) AS total_due
            FROM selected_consumable_orders
        ),
        selected_kids_sessions AS (
            SELECT
                k.status,
                k.payment_status,
                COALESCE(k.total_amount, 0) AS total_amount
            FROM kids_play_sessions k
            WHERE DATE(k.start_time) = :selectedDate
        ),
        kids_totals AS (
            SELECT
                COALESCE(SUM(CASE WHEN status = 'ENDED' THEN total_amount ELSE 0 END), 0) AS total_earnings,
                COALESCE(SUM(CASE WHEN total_amount > 0 AND payment_status <> 'PAID' THEN total_amount ELSE 0 END), 0) AS total_due
            FROM selected_kids_sessions
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
                sf.looser AS user_id,
                COALESCE(sf.payment_due, 0) AS due_amount
            FROM selected_frames sf
            WHERE sf.looser IS NOT NULL
              AND sf.payment_due > 0
              AND NOT EXISTS (
                  SELECT 1 FROM frame_players fp WHERE fp.frame_id = sf.id AND fp.amount_due IS NOT NULL
              )

            UNION ALL

            SELECT
                fp.user_id AS user_id,
                COALESCE(fp.amount_due, 0) AS due_amount
            FROM frame_players fp
            JOIN selected_frames sf ON fp.frame_id = sf.id
            WHERE fp.user_id IS NOT NULL
              AND fp.amount_due > 0

            UNION ALL

            SELECT
                co.user_id AS user_id,
                COALESCE(co.total_amount, 0) AS due_amount
            FROM consumable_orders co
            WHERE DATE(co.created_at) = :selectedDate
              AND COALESCE(co.total_amount, 0) > 0
              AND co.payment_status <> 'PAID'

            UNION ALL

            SELECT
                k.parent_user_id AS user_id,
                COALESCE(k.total_amount, 0) AS due_amount
            FROM kids_play_sessions k
            WHERE DATE(k.start_time) = :selectedDate
              AND COALESCE(k.total_amount, 0) > 0
              AND k.payment_status <> 'PAID'
        ),
        due_players AS (
            SELECT
                u.id AS user_id,
                u.name AS player_name,
                SUM(de.due_amount) AS due_amount
            FROM due_entries de
            JOIN users u ON de.user_id = u.id
            GROUP BY u.id, u.name
        )
        SELECT
            dp.user_id AS userId,
            t.total_earnings AS totalEarnings,
            t.total_due AS totalDue,
            dp.player_name AS playerName,
            dp.due_amount AS dueAmount
        FROM totals t
        LEFT JOIN due_players dp ON TRUE
        ORDER BY dp.due_amount DESC NULLS LAST
    """, nativeQuery = true)
    List<TodayEarningsProjection> findEarningsAnalyticsByDate(@Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT DISTINCT f FROM Frame f
        LEFT JOIN FETCH f.framePlayers fp
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.ENDED
        AND f.endTime IS NOT NULL
        AND FUNCTION('DATE', f.endTime) = :selectedDate
        AND (
            (f.looser.id = :userId AND f.paymentDue IS NOT NULL AND f.paymentDue > 0 AND NOT EXISTS (SELECT 1 FROM FramePlayer fpi WHERE fpi.frame = f AND fpi.amountDue IS NOT NULL))
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
        ORDER BY f.startTime ASC
    """)
    List<Frame> findDueFramesByUserAndDateOrderByStartTime(
            @Param("userId") Integer userId,
            @Param("selectedDate") LocalDate selectedDate);

    @Query("""
        SELECT COALESCE(SUM(
            CASE 
                WHEN fp.id IS NOT NULL AND fp.amountDue IS NOT NULL THEN fp.amountDue
                ELSE f.paymentDue
            END
        ), 0)
        FROM Frame f
        LEFT JOIN f.framePlayers fp ON fp.user.id = :userId AND fp.amountDue IS NOT NULL
        WHERE f.status = com.youngstersclub.app.enums.FrameStatus.ENDED
        AND f.endTime IS NOT NULL
        AND FUNCTION('DATE', f.endTime) = :selectedDate
        AND (
            (f.looser.id = :userId AND (fp.id IS NULL OR fp.amountDue IS NULL) AND f.paymentDue > 0)
            OR
            (fp.user.id = :userId AND fp.amountDue IS NOT NULL AND fp.amountDue > 0)
        )
    """)
    BigDecimal getTotalDueForUserByDate(
            @Param("userId") Integer userId,
            @Param("selectedDate") LocalDate selectedDate);
}
