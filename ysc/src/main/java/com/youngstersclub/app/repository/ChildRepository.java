package com.youngstersclub.app.repository;

import com.youngstersclub.app.entity.Child;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChildRepository extends JpaRepository<Child, Long> {
    interface BirthdayChildProjection {
        Long getChildId();
        String getChildName();
        Integer getParentUserId();
        String getParentName();
        String getParentPhone();
        Long getOrganizationId();
        String getOrganizationName();
        String getBaseBranchName();
    }

    List<Child> findByParentUser_IdOrderByCreatedAtDesc(Integer parentUserId);
    long countByParentUser_Id(Integer parentUserId);

    @Query(value = """
        SELECT
            c.id AS childId,
            c.name AS childName,
            p.id AS parentUserId,
            p.name AS parentName,
            p.phone AS parentPhone,
            o.id AS organizationId,
            o.name AS organizationName,
            b.name AS baseBranchName
        FROM children c
        JOIN users p ON p.id = c.parent_user_id
        JOIN organization_users ou ON ou.user_id = p.id
        JOIN organizations o ON o.id = ou.organization_id
        LEFT JOIN branches b ON b.id = ou.base_branch_id
        WHERE c.date_of_birth IS NOT NULL
          AND EXTRACT(MONTH FROM c.date_of_birth) = :month
          AND EXTRACT(DAY FROM c.date_of_birth) = :day
          AND COALESCE(p.is_active, true) = true
          AND COALESCE(ou.is_active, true) = true
          AND ou.role = 'CUSTOMER'
        ORDER BY o.name ASC, p.name ASC, c.name ASC
    """, nativeQuery = true)
    List<BirthdayChildProjection> findBirthdayChildrenByMonthAndDay(
            @Param("month") int month,
            @Param("day") int day);
}
