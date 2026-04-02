package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.SnookerTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnookerTableRepository extends JpaRepository<SnookerTable, Integer> {
}
