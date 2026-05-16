package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.dto.PlayerSummaryProjection;
import com.youngstersclub.app.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    interface DailyVisitedCustomerProjection {
        Integer getUserId();
        String getName();
        String getPhone();
    }

    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleInAndIsActiveTrue(List<UserRole> roles);
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

    @Query(value = """
           SELECT DISTINCT visited.user_id AS userId, visited.name AS name, visited.phone AS phone
           FROM (
               SELECT u.id AS user_id, u.name, u.phone
               FROM frames f
               JOIN frame_players fp ON fp.frame_id = f.id
               JOIN users u ON u.id = fp.user_id
               WHERE DATE(f.start_time) = :selectedDate
                 AND u.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone
               FROM consumable_orders co
               JOIN users u ON u.id = co.user_id
               WHERE DATE(co.created_at) = :selectedDate
                 AND u.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone
               FROM kids_play_sessions kps
               JOIN users u ON u.id = kps.parent_user_id
               WHERE DATE(kps.start_time) = :selectedDate
                 AND u.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone
               FROM users u
               WHERE DATE(u.created_at) = :selectedDate
                 AND u.role = 'CUSTOMER'
           ) visited
           ORDER BY visited.name ASC
           """, nativeQuery = true)
    List<DailyVisitedCustomerProjection> findDailyVisitedCustomers(@Param("selectedDate") java.time.LocalDate selectedDate);
}
