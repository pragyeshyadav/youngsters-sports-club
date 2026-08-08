package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.UserDue;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDueRepository extends JpaRepository<UserDue, Integer> {
    Optional<UserDue> findByUser_IdAndBranch_Id(Integer userId, Long branchId);

    @Query("""
        SELECT ud
        FROM UserDue ud
        WHERE ud.user.id = :userId
        AND ud.branch.id = :branchId
    """)
    Optional<UserDue> findByUserIdAndBranchId(@Param("userId") Integer userId, @Param("branchId") Long branchId);

    default Optional<UserDue> findByUserIdAndBranchId(Long userId, Long branchId) {
        if (userId == null || branchId == null) {
            return Optional.empty();
        }
        return findByUserIdAndBranchId(userId.intValue(), branchId);
    }
}
