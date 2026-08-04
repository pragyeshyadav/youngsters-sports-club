package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    interface DailyVisitedCustomerProjection {
        Integer getUserId();
        String getName();
        String getPhone();
    }

    interface DailyVisitedOrganizationProjection {
        Integer getUserId();
        String getName();
        String getPhone();
        Long getOrganizationId();
        String getOrganizationName();
        Long getBranchId();
        String getBranchName();
    }

    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleAndIsActiveTrue(UserRole role);
    List<User> findByRoleInAndIsActiveTrue(List<UserRole> roles);
    List<User> findByIdInAndRoleAndIsActiveTrue(List<Integer> ids, UserRole role);
    List<User> findTop10ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query("""
           SELECT u
           FROM User u
           WHERE COALESCE(u.isActive, true) = true
             AND (
                 LOWER(COALESCE(u.name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                 OR (:digitsQuery <> '' AND COALESCE(u.phone, '') LIKE CONCAT('%', :digitsQuery, '%'))
             )
           ORDER BY u.name ASC
           """)
    List<User> searchActiveUsers(@Param("query") String query, @Param("digitsQuery") String digitsQuery, Pageable pageable);

    @Query("""
           SELECT DISTINCT u
           FROM User u
           WHERE u.role = :role
             AND COALESCE(u.isActive, true) = true
             AND EXISTS (
                 SELECT fp.id
                 FROM FramePlayer fp
                 WHERE fp.user.id = u.id
             )
           ORDER BY u.name ASC
           """)
    List<User> findDistinctUsersWithFrameParticipation(@Param("role") UserRole role);

    @Query(value = """
           SELECT
               u.id AS userId,
               u.name AS name,
               u.email AS email,
               COUNT(fp.id) AS framesPlayed
           FROM users u
           LEFT JOIN frame_players fp ON fp.user_id = u.id
           GROUP BY u.id, u.name, u.email
           """, nativeQuery = true)
    List<PlayerSummaryBaseProjection> getAllPlayerSummaryBases();

    @Query(value = """
           SELECT
               u.id AS userId,
               u.name AS name,
               u.email AS email,
               COALESCE((
                   SELECT COUNT(fp.id)
                   FROM frame_players fp
                   JOIN frames f ON f.id = fp.frame_id
                   WHERE fp.user_id = u.id
                     AND f.branch_id = :branchId
               ), 0) AS framesPlayed
           FROM users u
           WHERE COALESCE(u.is_active, true) = true
             AND EXISTS (
                 SELECT 1
                 FROM organization_users ou
                 LEFT JOIN user_branch_access uba
                   ON uba.organization_user_id = ou.id
                  AND uba.branch_id = :branchId
                  AND COALESCE(uba.is_active, true) = true
                 WHERE ou.user_id = u.id
                   AND ou.organization_id = :organizationId
                   AND COALESCE(ou.is_active, true) = true
                   AND (ou.base_branch_id = :branchId OR uba.id IS NOT NULL)
             )
           ORDER BY u.name ASC, u.id ASC
           """, nativeQuery = true)
    List<PlayerSummaryBaseProjection> getPlayerSummaryBasesForBranch(
            @Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId);

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

    @Query(value = """
           SELECT DISTINCT
               visited.user_id AS userId,
               visited.name AS name,
               visited.phone AS phone,
               visited.organization_id AS organizationId,
               visited.organization_name AS organizationName,
               visited.branch_id AS branchId,
               visited.branch_name AS branchName
           FROM (
               SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
               FROM frames f
               JOIN frame_players fp ON fp.frame_id = f.id
               JOIN users u ON u.id = fp.user_id
               JOIN branches b ON b.id = f.branch_id
               JOIN organizations o ON o.id = b.organization_id
               JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
               WHERE DATE(f.start_time) = :selectedDate
                 AND COALESCE(u.is_active, true) = true
                 AND COALESCE(ou.is_active, true) = true
                 AND ou.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
               FROM consumable_orders co
               JOIN users u ON u.id = co.user_id
               JOIN branches b ON b.id = co.branch_id
               JOIN organizations o ON o.id = b.organization_id
               JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
               WHERE DATE(co.created_at) = :selectedDate
                 AND COALESCE(u.is_active, true) = true
                 AND COALESCE(ou.is_active, true) = true
                 AND ou.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
               FROM kids_play_sessions kps
               JOIN users u ON u.id = kps.parent_user_id
               JOIN branches b ON b.id = kps.branch_id
               JOIN organizations o ON o.id = b.organization_id
               JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
               WHERE DATE(kps.start_time) = :selectedDate
                 AND COALESCE(u.is_active, true) = true
                 AND COALESCE(ou.is_active, true) = true
                 AND ou.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
               FROM game_activity_orders gao
               JOIN users u ON u.id = gao.parent_user_id
               JOIN branches b ON b.id = gao.branch_id
               JOIN organizations o ON o.id = b.organization_id
               JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
               WHERE DATE(gao.created_at) = :selectedDate
                 AND COALESCE(u.is_active, true) = true
                 AND COALESCE(ou.is_active, true) = true
                 AND ou.role = 'CUSTOMER'

               UNION

               SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
               FROM organization_users ou
               JOIN users u ON u.id = ou.user_id
               JOIN organizations o ON o.id = ou.organization_id
               LEFT JOIN branches b ON b.id = ou.base_branch_id
               WHERE DATE(ou.created_at) = :selectedDate
                 AND COALESCE(u.is_active, true) = true
                 AND COALESCE(ou.is_active, true) = true
                 AND ou.role = 'CUSTOMER'
           ) visited
           ORDER BY visited.organization_name ASC, visited.name ASC, visited.branch_name ASC
           """, nativeQuery = true)
    List<DailyVisitedOrganizationProjection> findDailyVisitedCustomersByOrganization(
            @Param("selectedDate") java.time.LocalDate selectedDate);
}
