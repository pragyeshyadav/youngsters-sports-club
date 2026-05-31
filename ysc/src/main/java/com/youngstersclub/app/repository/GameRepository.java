package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Game;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findTop10ByIsActiveTrueAndGameNameContainingIgnoreCaseOrderByGameNameAsc(String query);
    List<Game> findByIdInAndIsActiveTrue(List<Long> ids);
}
