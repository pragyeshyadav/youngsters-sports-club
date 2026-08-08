package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.UserBranchAccess;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBranchAccessRepository extends JpaRepository<UserBranchAccess, Long> {
  @EntityGraph(attributePaths = {"branch", "organizationUser"})
  List<UserBranchAccess> findByOrganizationUserIdAndIsActiveTrue(Long organizationUserId);

  @EntityGraph(attributePaths = {"branch", "organizationUser"})
  List<UserBranchAccess> findByOrganizationUserId(Long organizationUserId);

  boolean existsByOrganizationUserIdAndBranchIdAndIsActiveTrue(Long organizationUserId, Long branchId);

  Optional<UserBranchAccess> findByOrganizationUserIdAndBranchId(Long organizationUserId, Long branchId);
}
