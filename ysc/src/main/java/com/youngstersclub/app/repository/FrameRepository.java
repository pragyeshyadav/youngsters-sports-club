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
        LEFT JOIN f.framePlayers fp
        WHERE f.status = :status
        AND (
            f.startedBy.id = :userId
            OR fp.user.id = :userId
        )
    """)
    Optional<Frame> findActiveFrameForUser(@Param("userId") Integer userId, @Param("status") FrameStatus status);

    @Query("""
        SELECT DISTINCT f FROM Frame f
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
}
