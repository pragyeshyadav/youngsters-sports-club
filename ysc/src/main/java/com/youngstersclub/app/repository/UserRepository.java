package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleAndIsActiveTrue(UserRole role);
    List<User> findByRoleInAndIsActiveTrue(List<UserRole> roles);
    List<User> findByIdInAndRoleAndIsActiveTrue(List<Integer> ids, UserRole role);
    List<User> findTop10ByNameContainingIgnoreCaseOrderByNameAsc(String name);

    @Query("""
            select ou.user
            from OrganizationUser ou
            where ou.organization.id = :organizationId
              and ou.role = :role
              and coalesce(ou.isActive, true) = true
              and ou.user is not null
              and coalesce(ou.user.isActive, true) = true
              and (
                    :branchId is null
                    or (ou.baseBranch is not null and ou.baseBranch.id = :branchId)
                    or exists (
                        select 1
                        from UserBranchAccess uba
                        where uba.organizationUser = ou
                          and uba.branch.id = :branchId
                          and coalesce(uba.isActive, true) = true
                    )
              )
            order by ou.user.name asc, ou.user.id asc
            """)
    List<User> findActiveUsersByRoleAndOrganizationAndOptionalBranch(
            @Param("role") UserRole role,
            @Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId);

    @Query("""
            select ou.user
            from OrganizationUser ou
            where ou.organization.id = :organizationId
              and ou.role = :role
              and ou.user.id in :userIds
              and coalesce(ou.isActive, true) = true
              and ou.user is not null
              and coalesce(ou.user.isActive, true) = true
              and (
                    :branchId is null
                    or (ou.baseBranch is not null and ou.baseBranch.id = :branchId)
                    or exists (
                        select 1
                        from UserBranchAccess uba
                        where uba.organizationUser = ou
                          and uba.branch.id = :branchId
                          and coalesce(uba.isActive, true) = true
                    )
              )
            order by ou.user.name asc, ou.user.id asc
            """)
    List<User> findActiveUsersByIdsAndRoleAndOrganizationAndOptionalBranch(
            @Param("userIds") List<Integer> userIds,
            @Param("role") UserRole role,
            @Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId);

    @Query("""
            select ou.user
            from OrganizationUser ou
            where ou.organization.id = :organizationId
              and ou.role = :role
              and coalesce(ou.isActive, true) = true
              and ou.user is not null
              and coalesce(ou.user.isActive, true) = true
              and exists (
                    select 1
                    from FramePlayer fp
                    join fp.frame f
                    where fp.user.id = ou.user.id
                      and (:branchId is null or f.branch.id = :branchId)
              )
              and (
                    :branchId is null
                    or (ou.baseBranch is not null and ou.baseBranch.id = :branchId)
                    or exists (
                        select 1
                        from UserBranchAccess uba
                        where uba.organizationUser = ou
                          and uba.branch.id = :branchId
                          and coalesce(uba.isActive, true) = true
                    )
              )
            order by ou.user.name asc, ou.user.id asc
            """)
    List<User> findDistinctUsersWithFrameParticipationByRoleAndOrganizationAndOptionalBranch(
            @Param("role") UserRole role,
            @Param("organizationId") Long organizationId,
            @Param("branchId") Long branchId);
}
