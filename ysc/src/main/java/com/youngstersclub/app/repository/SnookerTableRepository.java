package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.SnookerTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SnookerTableRepository extends JpaRepository<SnookerTable, Long> {
    List<SnookerTable> findByIsAvailable(Boolean isAvailable);
    List<SnookerTable> findByIsAvailableTrueOrderByIdAsc();

    @Query("""
        SELECT t FROM SnookerTable t
        WHERE t.isAvailable = true
        AND t.id NOT IN (
            SELECT f.snookerTable.id FROM Frame f
            WHERE f.status = com.youngstersclub.app.enums.FrameStatus.STARTED
            AND f.endTime IS NULL
        )
        ORDER BY t.id ASC
    """)
    List<SnookerTable> findAvailableTablesSafe();
}
