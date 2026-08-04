package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Game;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findByBranch_IdAndIsActiveTrueOrderByGameNameAsc(Long branchId);
    List<Game> findTop10ByBranch_IdAndIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(Long branchId, String query);
    List<Game> findByIdInAndBranch_IdAndIsActiveTrue(List<Long> ids, Long branchId);
    java.util.Optional<Game> findByIdAndBranch_Id(Long id, Long branchId);

    List<Game> findTop10ByIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(String query);
    List<Game> findByIdInAndIsActiveTrue(List<Long> ids);
}
