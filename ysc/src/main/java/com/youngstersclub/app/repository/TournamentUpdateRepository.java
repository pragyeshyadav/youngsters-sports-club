package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.TournamentUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TournamentUpdateRepository extends JpaRepository<TournamentUpdate, Long> {
}
