package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.dto.PlayerSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
    List<User> findTop10ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query(value = "SELECT " +
                   "u.id as userId, " +
                   "u.name as name, " +
                   "u.email as email, " +
                   "COUNT(fp.id) as framesPlayed, " +
                   "COALESCE(SUM(CASE WHEN f.looser = u.id THEN f.payment_due ELSE 0 END), 0) as totalDue " +
                   "FROM users u " +
                   "LEFT JOIN frame_players fp ON fp.user_id = u.id " +
                   "LEFT JOIN frames f ON f.id = fp.frame_id " +
                   "GROUP BY u.id, u.name, u.email " +
                   "ORDER BY totalDue DESC",
           countQuery = "SELECT count(u.id) FROM users u",
           nativeQuery = true)
    Page<PlayerSummaryProjection> getPlayerSummaries(Pageable pageable);
}
