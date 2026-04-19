package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Child;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {
    List<Child> findByParentUser_IdOrderByCreatedAtDesc(Integer parentUserId);
    long countByParentUser_Id(Integer parentUserId);
}
