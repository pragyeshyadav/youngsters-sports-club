package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {

    List<UserSearchResultDto> searchActiveUserSummaries(String query, String digitsQuery, Pageable pageable);

    List<UserSearchResultDto> searchActiveUserSummariesForOrganizationBranch(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId);

    List<UserSearchResultDto> searchActiveUserSummariesForOrganizationScope(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId);

    List<UserSearchResultDto> searchActiveUserSummariesForOrganizationScopeAndRole(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId,
            UserRole role);

    List<User> findDistinctUsersWithFrameParticipation(UserRole role);

    List<PlayerSummaryBaseProjection> getPlayerSummaryBasesForBranch(Long organizationId, Long branchId);
}
