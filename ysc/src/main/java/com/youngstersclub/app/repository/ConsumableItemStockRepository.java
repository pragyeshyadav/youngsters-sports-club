package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.ConsumableItemStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumableItemStockRepository extends JpaRepository<ConsumableItemStock, Long> {
    java.util.Optional<ConsumableItemStock> findByIdAndBranch_Id(Long id, Long branchId);
}
