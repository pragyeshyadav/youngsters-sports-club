package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.dto.PlayerSummaryBaseRow;
import com.youngstersclub.app.dto.UserSearchResultDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.enums.UserRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public class UserRepositoryImpl implements UserRepositoryCustom {

    private static final String ACTIVE_USER_SUMMARY_SEARCH_SQL = """
            SELECT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            WHERE COALESCE(u.is_active, true) = true
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final String PLAYER_SUMMARY_BY_BRANCH_SQL = """
            SELECT
                u.id AS user_id,
                u.name AS name,
                u.email AS email,
                COALESCE((
                    SELECT COUNT(fp.id)
                    FROM frame_players fp
                    JOIN frames f ON f.id = fp.frame_id
                    WHERE fp.user_id = u.id
                      AND f.branch_id = :branchId
                ), 0) AS frames_played
            FROM users u
            WHERE COALESCE(u.is_active, true) = true
              AND EXISTS (
                  SELECT 1
                  FROM organization_users ou
                  LEFT JOIN user_branch_access uba
                    ON uba.organization_user_id = ou.id
                   AND uba.branch_id = :branchId
                   AND COALESCE(uba.is_active, true) = true
                  WHERE ou.user_id = u.id
                    AND ou.organization_id = :organizationId
                    AND COALESCE(ou.is_active, true) = true
                    AND (ou.base_branch_id = :branchId OR uba.id IS NOT NULL)
              )
            ORDER BY u.name ASC, u.id ASC
            """;

    private static final String ACTIVE_USER_SUMMARY_SEARCH_BY_BRANCH_SQL = """
            SELECT DISTINCT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            JOIN organization_users ou
              ON ou.user_id = u.id
             AND ou.organization_id = :organizationId
             AND COALESCE(ou.is_active, true) = true
            LEFT JOIN user_branch_access uba
              ON uba.organization_user_id = ou.id
             AND uba.branch_id = :branchId
             AND COALESCE(uba.is_active, true) = true
            WHERE COALESCE(u.is_active, true) = true
              AND (ou.base_branch_id = :branchId OR uba.id IS NOT NULL)
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final String ACTIVE_USER_SUMMARY_SEARCH_BY_ORG_SCOPE_SQL = """
            SELECT DISTINCT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            JOIN organization_users ou
              ON ou.user_id = u.id
             AND ou.organization_id = :organizationId
             AND COALESCE(ou.is_active, true) = true
            LEFT JOIN user_branch_access uba
              ON uba.organization_user_id = ou.id
             AND uba.branch_id = :branchId
             AND COALESCE(uba.is_active, true) = true
            WHERE COALESCE(u.is_active, true) = true
              AND (
                    :branchId IS NULL
                    OR ou.base_branch_id = :branchId
                    OR uba.id IS NOT NULL
              )
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final String ACTIVE_USER_SUMMARY_SEARCH_BY_ORGANIZATION_SQL = """
            SELECT DISTINCT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            JOIN organization_users ou
              ON ou.user_id = u.id
             AND ou.organization_id = :organizationId
             AND COALESCE(ou.is_active, true) = true
            WHERE COALESCE(u.is_active, true) = true
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final String ACTIVE_USER_SUMMARY_SEARCH_BY_ORG_SCOPE_AND_ROLE_SQL = """
            SELECT DISTINCT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            JOIN organization_users ou
              ON ou.user_id = u.id
             AND ou.organization_id = :organizationId
             AND ou.role = :role
             AND COALESCE(ou.is_active, true) = true
            LEFT JOIN user_branch_access uba
              ON uba.organization_user_id = ou.id
             AND uba.branch_id = :branchId
             AND COALESCE(uba.is_active, true) = true
            WHERE COALESCE(u.is_active, true) = true
              AND (
                    :branchId IS NULL
                    OR ou.base_branch_id = :branchId
                    OR uba.id IS NOT NULL
              )
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final String ACTIVE_USER_SUMMARY_SEARCH_BY_ORGANIZATION_AND_ROLE_SQL = """
            SELECT DISTINCT
                u.id AS id,
                u.name AS name,
                u.email AS email,
                u.google_id AS google_id,
                u.profile_pic AS profile_pic,
                u.phone AS phone,
                COALESCE(u.is_active, true) AS is_active,
                u.role AS role
            FROM users u
            JOIN organization_users ou
              ON ou.user_id = u.id
             AND ou.organization_id = :organizationId
             AND ou.role = :role
             AND COALESCE(ou.is_active, true) = true
            WHERE COALESCE(u.is_active, true) = true
              AND (
                    (:includeTextMatch = true AND (
                        LOWER(COALESCE(u.name, '')) LIKE :query
                        OR LOWER(COALESCE(u.email, '')) LIKE :query
                    ))
                    OR (:includeDigitsMatch = true AND COALESCE(u.phone, '') LIKE :digitsQuery)
              )
            ORDER BY u.name ASC, u.id ASC
            LIMIT :limit
            """;

    private static final RowMapper<PlayerSummaryBaseProjection> PLAYER_SUMMARY_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public PlayerSummaryBaseProjection mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new PlayerSummaryBaseRow(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getLong("frames_played"));
                }
            };

    private static final RowMapper<UserSearchResultDto> USER_SEARCH_RESULT_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public UserSearchResultDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new UserSearchResultDto(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("google_id"),
                            rs.getString("profile_pic"),
                            rs.getString("phone"),
                            rs.getBoolean("is_active"),
                            rs.getString("role"));
                }
            };

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(EntityManager entityManager, NamedParameterJdbcTemplate jdbcTemplate) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<UserSearchResultDto> searchActiveUserSummaries(String query, String digitsQuery, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedDigitsQuery = digitsQuery == null ? "" : digitsQuery.trim();
        if (normalizedQuery.isEmpty() && normalizedDigitsQuery.isEmpty()) {
            return List.of();
        }
        boolean includeTextMatch = !normalizedQuery.isEmpty();
        boolean includeDigitsMatch = !normalizedDigitsQuery.isEmpty();
        int limit = pageable == null ? 10 : pageable.getPageSize();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("includeTextMatch", includeTextMatch)
                .addValue("includeDigitsMatch", includeDigitsMatch)
                .addValue("query", "%" + normalizedQuery + "%")
                .addValue("digitsQuery", "%" + normalizedDigitsQuery + "%")
                .addValue("limit", limit);
        return jdbcTemplate.query(ACTIVE_USER_SUMMARY_SEARCH_SQL, params, USER_SEARCH_RESULT_ROW_MAPPER);
    }

    @Override
    public List<UserSearchResultDto> searchActiveUserSummariesForOrganizationBranch(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId) {
        return searchActiveUserSummariesForOrganizationScope(
                query,
                digitsQuery,
                pageable,
                organizationId,
                branchId);
    }

    @Override
    public List<UserSearchResultDto> searchActiveUserSummariesForOrganizationScope(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedDigitsQuery = digitsQuery == null ? "" : digitsQuery.trim();
        if ((normalizedQuery.isEmpty() && normalizedDigitsQuery.isEmpty())
                || organizationId == null) {
            return List.of();
        }
        boolean includeTextMatch = !normalizedQuery.isEmpty();
        boolean includeDigitsMatch = !normalizedDigitsQuery.isEmpty();
        int limit = pageable == null ? 10 : pageable.getPageSize();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("includeTextMatch", includeTextMatch)
                .addValue("includeDigitsMatch", includeDigitsMatch)
                .addValue("query", "%" + normalizedQuery + "%")
                .addValue("digitsQuery", "%" + normalizedDigitsQuery + "%")
                .addValue("limit", limit);
        if (branchId == null) {
            return jdbcTemplate.query(ACTIVE_USER_SUMMARY_SEARCH_BY_ORGANIZATION_SQL, params, USER_SEARCH_RESULT_ROW_MAPPER);
        }

        params.addValue("branchId", branchId);
        return jdbcTemplate.query(ACTIVE_USER_SUMMARY_SEARCH_BY_ORG_SCOPE_SQL, params, USER_SEARCH_RESULT_ROW_MAPPER);
    }

    @Override
    public List<UserSearchResultDto> searchActiveUserSummariesForOrganizationScopeAndRole(
            String query,
            String digitsQuery,
            Pageable pageable,
            Long organizationId,
            Long branchId,
            UserRole role) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedDigitsQuery = digitsQuery == null ? "" : digitsQuery.trim();
        if ((normalizedQuery.isEmpty() && normalizedDigitsQuery.isEmpty())
                || organizationId == null
                || role == null) {
            return List.of();
        }
        boolean includeTextMatch = !normalizedQuery.isEmpty();
        boolean includeDigitsMatch = !normalizedDigitsQuery.isEmpty();
        int limit = pageable == null ? 10 : pageable.getPageSize();

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("role", role.name())
                .addValue("includeTextMatch", includeTextMatch)
                .addValue("includeDigitsMatch", includeDigitsMatch)
                .addValue("query", "%" + normalizedQuery + "%")
                .addValue("digitsQuery", "%" + normalizedDigitsQuery + "%")
                .addValue("limit", limit);
        if (branchId == null) {
            return jdbcTemplate.query(
                    ACTIVE_USER_SUMMARY_SEARCH_BY_ORGANIZATION_AND_ROLE_SQL,
                    params,
                    USER_SEARCH_RESULT_ROW_MAPPER);
        }

        params.addValue("branchId", branchId);
        return jdbcTemplate.query(
                ACTIVE_USER_SUMMARY_SEARCH_BY_ORG_SCOPE_AND_ROLE_SQL,
                params,
                USER_SEARCH_RESULT_ROW_MAPPER);
    }

    @Override
    public List<User> findDistinctUsersWithFrameParticipation(UserRole role) {
        return entityManager.createQuery(
                        """
                        SELECT DISTINCT u
                        FROM User u, FramePlayer fp
                        WHERE u.id = fp.user.id
                          AND u.role = :role
                          AND COALESCE(u.isActive, true) = true
                        ORDER BY u.name ASC
                        """,
                        User.class)
                .setParameter("role", role)
                .getResultList();
    }

    @Override
    public List<PlayerSummaryBaseProjection> getPlayerSummaryBasesForBranch(Long organizationId, Long branchId) {
        if (organizationId == null || branchId == null) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("branchId", branchId);
        return jdbcTemplate.query(PLAYER_SUMMARY_BY_BRANCH_SQL, params, PLAYER_SUMMARY_ROW_MAPPER);
    }
}
