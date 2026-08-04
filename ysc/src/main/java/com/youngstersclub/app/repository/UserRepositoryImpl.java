package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryBaseProjection;
import com.youngstersclub.app.dto.PlayerSummaryBaseRow;
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

    private final EntityManager entityManager;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UserRepositoryImpl(EntityManager entityManager, NamedParameterJdbcTemplate jdbcTemplate) {
        this.entityManager = entityManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<User> searchActiveUsers(String query, String digitsQuery, Pageable pageable) {
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedDigitsQuery = digitsQuery == null ? "" : digitsQuery.trim();
        if (normalizedQuery.isEmpty() && normalizedDigitsQuery.isEmpty()) {
            return List.of();
        }

        StringBuilder jpql = new StringBuilder("SELECT u FROM User u WHERE COALESCE(u.isActive, true) = true");
        boolean includeTextMatch = !normalizedQuery.isEmpty();
        boolean includeDigitsMatch = !normalizedDigitsQuery.isEmpty();

        if (includeTextMatch || includeDigitsMatch) {
            jpql.append(" AND (");
            boolean needsOr = false;
            if (includeTextMatch) {
                jpql.append("LOWER(COALESCE(u.name, '')) LIKE :query")
                        .append(" OR LOWER(COALESCE(u.email, '')) LIKE :query");
                needsOr = true;
            }
            if (includeDigitsMatch) {
                if (needsOr) {
                    jpql.append(" OR ");
                }
                jpql.append("COALESCE(u.phone, '') LIKE :digitsQuery");
            }
            jpql.append(")");
        }
        jpql.append(" ORDER BY u.name ASC");

        TypedQuery<User> typedQuery = entityManager.createQuery(jpql.toString(), User.class);
        if (includeTextMatch) {
            typedQuery.setParameter("query", "%" + normalizedQuery + "%");
        }
        if (includeDigitsMatch) {
            typedQuery.setParameter("digitsQuery", "%" + normalizedDigitsQuery + "%");
        }
        if (pageable != null) {
            typedQuery.setFirstResult((int) pageable.getOffset());
            typedQuery.setMaxResults(pageable.getPageSize());
        }
        return typedQuery.getResultList();
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
