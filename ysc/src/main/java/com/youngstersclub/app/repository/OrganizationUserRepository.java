package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.OrganizationUser;
import com.youngstersclub.app.enums.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationUserRepository extends JpaRepository<OrganizationUser, Long> {
  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByUserIdAndIsActiveTrue(Integer userId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByUserId(Integer userId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  Optional<OrganizationUser> findByUserIdAndOrganizationIdAndIsActiveTrue(Integer userId, Long organizationId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  Optional<OrganizationUser> findByUserIdAndOrganizationId(Integer userId, Long organizationId);

  @EntityGraph(attributePaths = {"organization", "baseBranch", "user"})
  List<OrganizationUser> findByRoleAndIsActiveTrue(UserRole role);
}
