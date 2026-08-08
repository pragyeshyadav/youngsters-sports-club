package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer>, UserRepositoryCustom {

    Optional<User> findByEmail(String email);
    Optional<User> findByGoogleId(String googleId);
    Optional<User> findByPhone(String phone);
    List<User> findByRoleAndIsActiveTrue(UserRole role);
    List<User> findByRoleInAndIsActiveTrue(List<UserRole> roles);
    List<User> findByIdInAndRoleAndIsActiveTrue(List<Integer> ids, UserRole role);
    List<User> findTop10ByNameContainingIgnoreCaseOrderByNameAsc(String name);
}
