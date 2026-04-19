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
    Optional<KidsPlaySession> findActiveByParentUserId(@Param("parentUserId") Integer parentUserId);

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
}
