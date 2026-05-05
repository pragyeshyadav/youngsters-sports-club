package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    List<Tournament> findByEventNameAndIsActiveTrue(String eventName);
}
