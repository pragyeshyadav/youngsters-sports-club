package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.BranchExpense;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BranchExpenseRepository extends JpaRepository<BranchExpense, Long> {

    List<BranchExpense> findByBranch_IdAndExpenseDateGreaterThanEqualAndExpenseDateLessThanAndIsActiveTrueOrderByExpenseDateDescCreatedAtDescIdDesc(
            Long branchId,
            LocalDate startInclusive,
            LocalDate endExclusive);
}
