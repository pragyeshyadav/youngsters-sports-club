package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.TournamentRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TournamentRegistrationRepository extends JpaRepository<TournamentRegistration, Long> {
    boolean existsByTournamentIdAndUserId(Long tournamentId, Integer userId);
}
