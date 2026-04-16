package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.ConsumableOrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumableOrderItemRepository extends JpaRepository<ConsumableOrderItem, Long> {

    @Query("""
        SELECT coi FROM ConsumableOrderItem coi
        WHERE coi.order.id = :orderId
    """)
    List<ConsumableOrderItem> findByOrderId(@Param("orderId") Long orderId);
}
