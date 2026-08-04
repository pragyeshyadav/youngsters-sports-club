package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.CustomerFeedback;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, Long> {
    Optional<CustomerFeedback> findByIdAndBranch_Id(Long id, Long branchId);
    List<CustomerFeedback> findByBranch_IdOrderByCreatedAtDesc(Long branchId);
}
