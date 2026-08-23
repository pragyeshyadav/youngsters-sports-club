package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.ConsumableItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConsumableItemRepository extends JpaRepository<ConsumableItem, Long> {
    interface ConsumableStockReportProjection {
        Long getItemId();
        String getItemName();
        Long getStockAdded();
        Long getSoldQuantity();
        Long getAvailableStock();
    }

    List<ConsumableItem> findByBranch_IdAndIsActiveTrueOrderByNameAsc(Long branchId);
    List<ConsumableItem> findTop10ByBranch_IdAndIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(Long branchId, String name);
    List<ConsumableItem> findByIdInAndBranch_IdAndIsActiveTrue(List<Long> ids, Long branchId);
    java.util.Optional<ConsumableItem> findByIdAndBranch_Id(Long id, Long branchId);

    List<ConsumableItem> findByIsActiveTrue();
    List<ConsumableItem> findTop10ByIsActiveTrueAndNameContainingIgnoreCaseOrderByNameAsc(String name);
    List<ConsumableItem> findByIdInAndIsActiveTrue(List<Long> ids);

    @Query(value = """
        SELECT
            ci.id AS itemId,
            ci.name AS itemName,
            COALESCE(stock_totals.stock_added, 0) AS stockAdded,
            COALESCE(sold_totals.sold_quantity, 0) AS soldQuantity,
            COALESCE(stock_totals.stock_added, 0) - COALESCE(sold_totals.sold_quantity, 0) AS availableStock
        FROM consumable_items ci
        LEFT JOIN (
            SELECT
                cis.item_id,
                SUM(cis.quantity_added) AS stock_added
            FROM consumable_item_stock cis
            WHERE cis.branch_id = :branchId
              AND EXTRACT(MONTH FROM cis.created_at) = :month
              AND EXTRACT(YEAR FROM cis.created_at) = :year
            GROUP BY cis.item_id
        ) stock_totals ON stock_totals.item_id = ci.id
        LEFT JOIN (
            SELECT
                coi.item_id,
                SUM(coi.quantity) AS sold_quantity
            FROM consumable_order_items coi
            JOIN consumable_orders co ON co.id = coi.order_id
            WHERE co.branch_id = :branchId
              AND EXTRACT(MONTH FROM co.created_at) = :month
              AND EXTRACT(YEAR FROM co.created_at) = :year
            GROUP BY coi.item_id
        ) sold_totals ON sold_totals.item_id = ci.id
        WHERE ci.branch_id = :branchId
        ORDER BY ci.name ASC
    """, nativeQuery = true)
    List<ConsumableStockReportProjection> getConsumableStockReport(
            @Param("branchId") Long branchId,
            @Param("month") int month,
            @Param("year") int year);
}
