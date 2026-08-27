package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Organization;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
  List<Organization> findAllByOrderByNameAsc();
  List<Organization> findByIsActiveTrueOrderByNameAsc();
  Optional<Organization> findByIdAndIsActiveTrue(Long id);
}
