package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.UserDue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDueRepository extends JpaRepository<UserDue, Integer> {
}
