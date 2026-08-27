package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Branch;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
  List<Branch> findByOrganizationIdOrderByNameAsc(Long organizationId);
  List<Branch> findByOrganizationIdAndIsActiveTrueOrderByNameAsc(Long organizationId);
  Optional<Branch> findByIdAndOrganizationId(Long id, Long organizationId);
  Optional<Branch> findByIdAndOrganizationIdAndIsActiveTrue(Long id, Long organizationId);
  List<Branch> findByOrganizationIdAndIdInAndIsActiveTrue(Long organizationId, Collection<Long> ids);
}
