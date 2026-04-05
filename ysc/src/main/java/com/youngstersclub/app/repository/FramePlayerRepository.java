package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.FramePlayer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FramePlayerRepository extends JpaRepository<FramePlayer, Integer> {
    List<FramePlayer> findByFrame_Id(Integer frameId);
}
