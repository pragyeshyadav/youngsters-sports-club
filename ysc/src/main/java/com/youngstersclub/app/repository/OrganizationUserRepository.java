package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, Long> {
  interface ActiveCustomerMembershipProjection {
    Long getOrganizationId();
    String getOrganizationName();
    Integer getUserId();
    String getUserName();
    String getPhone();
    Long getBaseBranchId();
    String getBaseBranchName();
  }

  interface ActiveBranchStaffProjection {
    Integer getUserId();
    String getName();
    UserRole getRole();
  }

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByUserIdAndIsActiveTrue(Integer userId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByUserId(Integer userId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  Optional<OrganizationUser> findByUserIdAndOrganizationIdAndIsActiveTrue(Integer userId, Long organizationId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  Optional<OrganizationUser> findByUserIdAndOrganizationId(Integer userId, Long organizationId);

  long countByBaseBranchIdAndIsActiveTrue(Long baseBranchId);

  @EntityGraph(attributePaths = {"baseBranch", "user"})
  List<OrganizationUser> findByOrganization_IdAndRoleAndIsActiveTrue(Long organizationId, UserRole role);

  @Query("""
      SELECT COUNT(ou)
      FROM OrganizationUser ou
      WHERE ou.user.id = :userId
        AND ou.id <> :excludeMembershipId
        AND ou.isActive = true
        AND ou.role IN :roles
  """)
  long countOtherActiveMembershipsByRoles(
          @Param("userId") Integer userId,
          @Param("excludeMembershipId") Long excludeMembershipId,
          @Param("roles") List<UserRole> roles);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByRoleAndIsActiveTrue(UserRole role);

  @Query("""
      SELECT DISTINCT ou.organization.id
      FROM OrganizationUser ou
      WHERE ou.role = :role
        AND ou.isActive = true
        AND ou.user IS NOT NULL
        AND ou.user.isActive = true
        AND ou.organization IS NOT NULL
        AND ou.organization.isActive = true
      ORDER BY ou.organization.id ASC
  """)
  List<Long> findDistinctActiveOrganizationIdsByRole(@Param("role") UserRole role);

  @Query("""
      SELECT
          ou.organization.id AS organizationId,
          ou.organization.name AS organizationName,
          ou.user.id AS userId,
          ou.user.name AS userName,
          ou.user.phone AS phone,
          ou.baseBranch.id AS baseBranchId,
          ou.baseBranch.name AS baseBranchName
      FROM OrganizationUser ou
      WHERE ou.role = :role
        AND ou.organization.id = :organizationId
        AND ou.isActive = true
        AND ou.user IS NOT NULL
        AND ou.user.isActive = true
        AND ou.organization IS NOT NULL
        AND ou.organization.isActive = true
      ORDER BY ou.user.id ASC
  """)
  List<ActiveCustomerMembershipProjection> findActiveCustomerMembershipsByRoleAndOrganizationId(
          @Param("role") UserRole role,
          @Param("organizationId") Long organizationId);

  @Query("""
      SELECT
          ou.user.id AS userId,
          ou.user.name AS name,
          ou.role AS role
      FROM OrganizationUser ou
      WHERE ou.organization.id = :organizationId
        AND ou.role IN :roles
        AND ou.isActive = true
        AND ou.user IS NOT NULL
        AND ou.user.isActive = true
        AND (
            (ou.baseBranch IS NOT NULL AND ou.baseBranch.id = :branchId)
            OR EXISTS (
                SELECT 1
                FROM UserBranchAccess uba
                WHERE uba.organizationUser = ou
                  AND uba.branch.id = :branchId
                  AND uba.isActive = true
            )
        )
      ORDER BY ou.user.name ASC, ou.user.id ASC
  """)
  List<ActiveBranchStaffProjection> findActiveStaffByOrganizationIdAndBranchIdAndRoles(
          @Param("organizationId") Long organizationId,
          @Param("branchId") Long branchId,
          @Param("roles") List<UserRole> roles);

  @Query("""
      SELECT DISTINCT LOWER(TRIM(ou.user.email))
      FROM OrganizationUser ou
      WHERE ou.organization.id = :organizationId
        AND ou.role IN :roles
        AND ou.isActive = true
        AND ou.user IS NOT NULL
        AND ou.user.email IS NOT NULL
        AND TRIM(ou.user.email) <> ''
        AND ou.user.isActive = true
        AND ou.organization IS NOT NULL
        AND ou.organization.isActive = true
      ORDER BY LOWER(TRIM(ou.user.email)) ASC
  """)
  List<String> findActiveRecipientEmailsByOrganizationIdAndRoles(
          @Param("organizationId") Long organizationId,
          @Param("roles") List<UserRole> roles);
}
