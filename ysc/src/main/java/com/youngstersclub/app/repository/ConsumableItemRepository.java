package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.ConsumableItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumableItemRepository extends JpaRepository<ConsumableItem, Long> {
    List<ConsumableItem> findByIsActiveTrue();
    List<ConsumableItem> findTop10ByIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String name);
    List<ConsumableItem> findByIdInAndIsActiveTrue(List<Long> ids);
}
