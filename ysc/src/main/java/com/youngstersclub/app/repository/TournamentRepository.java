package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findByBranch_IdAndIsActiveTrue(Long branchId);
    Optional<Tournament> findByIdAndBranch_Id(Long id, Long branchId);
    Optional<Tournament> findByIdAndBranch_IdAndIsActiveTrue(Long id, Long branchId);
    List<Tournament> findByBranch_IdAndEventNameAndIsActiveTrueOrderByNameAsc(Long branchId, String eventName);
    List<Tournament> findByEventNameAndIsActiveTrue(String eventName);
}
