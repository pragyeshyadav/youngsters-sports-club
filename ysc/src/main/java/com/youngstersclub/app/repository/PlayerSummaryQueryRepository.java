package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.PlayerSummaryDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlayerSummaryQueryRepository {

    private static final String ELIGIBLE_USERS_CTE = """
            WITH eligible_users AS (
                SELECT DISTINCT
                    u.id,
                    u.name,
                    u.email
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
            ),
            frame_counts AS (
                SELECT
                    fp.user_id,
                    COUNT(fp.id) AS frames_played
                FROM frame_players fp
                JOIN frames f ON f.id = fp.frame_id
                WHERE f.branch_id = :branchId
                GROUP BY fp.user_id
            ),
            frame_due_totals AS (
                SELECT
                    due_rows.user_id,
                    SUM(due_rows.amount) AS amount
                FROM (
                    SELECT
                        f.looser AS user_id,
                        COALESCE(f.payment_due, 0) AS amount
                    FROM frames f
                    WHERE f.branch_id = :branchId
                      AND f.looser IS NOT NULL
                      AND COALESCE(f.payment_due, 0) > 0
                      AND NOT EXISTS (
                          SELECT 1
                          FROM frame_players fp
                          WHERE fp.frame_id = f.id
                            AND fp.amount_due IS NOT NULL
                      )

                    UNION ALL

                    SELECT
                        fp.user_id AS user_id,
                        COALESCE(fp.amount_due, 0) AS amount
                    FROM frame_players fp
                    JOIN frames f ON f.id = fp.frame_id
                    WHERE f.branch_id = :branchId
                      AND fp.user_id IS NOT NULL
                      AND COALESCE(fp.amount_due, 0) > 0
                ) due_rows
                GROUP BY due_rows.user_id
            ),
            consumable_due_totals AS (
                SELECT
                    co.user_id,
                    SUM(COALESCE(co.total_amount, 0)) AS amount
                FROM consumable_orders co
                WHERE co.branch_id = :branchId
                  AND COALESCE(co.total_amount, 0) > 0
                  AND co.payment_status <> 'PAID'
                GROUP BY co.user_id
            ),
            kids_due_totals AS (
                SELECT
                    k.parent_user_id AS user_id,
                    SUM(COALESCE(k.total_amount, 0)) AS amount
                FROM kids_play_sessions k
                WHERE k.branch_id = :branchId
                  AND COALESCE(k.total_amount, 0) > 0
                  AND k.payment_status <> 'PAID'
                GROUP BY k.parent_user_id
            ),
            activity_due_totals AS (
                SELECT
                    g.parent_user_id AS user_id,
                    SUM(COALESCE(g.total_amount, 0)) AS amount
                FROM game_activity_orders g
                WHERE g.branch_id = :branchId
                  AND COALESCE(g.total_amount, 0) > 0
                  AND COALESCE(g.is_paid, false) = false
                GROUP BY g.parent_user_id
            )
            """;

    private static final String PAGE_SQL = ELIGIBLE_USERS_CTE + """
            SELECT
                eu.id AS user_id,
                eu.name AS name,
                eu.email AS email,
                COALESCE(fc.frames_played, 0) AS frames_played,
                COALESCE(fd.amount, 0)
                    + COALESCE(cd.amount, 0)
                    + COALESCE(kd.amount, 0)
                    + COALESCE(ad.amount, 0) AS total_due
            FROM eligible_users eu
            LEFT JOIN frame_counts fc ON fc.user_id = eu.id
            LEFT JOIN frame_due_totals fd ON fd.user_id = eu.id
            LEFT JOIN consumable_due_totals cd ON cd.user_id = eu.id
            LEFT JOIN kids_due_totals kd ON kd.user_id = eu.id
            LEFT JOIN activity_due_totals ad ON ad.user_id = eu.id
            ORDER BY
                total_due DESC,
                LOWER(eu.name) ASC NULLS LAST,
                eu.id ASC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = ELIGIBLE_USERS_CTE + """
            SELECT COUNT(*) FROM eligible_users
            """;

    private static final RowMapper<PlayerSummaryDto> PLAYER_SUMMARY_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public PlayerSummaryDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new PlayerSummaryDto(
                            rs.getInt("user_id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getLong("frames_played"),
                            rs.getBigDecimal("total_due"));
                }
            };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PlayerSummaryQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PlayerSummaryDto> findPlayerSummariesForBranch(
            Long organizationId,
            Long branchId,
            int limit,
            long offset) {
        if (organizationId == null || branchId == null || limit <= 0 || offset < 0) {
            return List.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("branchId", branchId)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbcTemplate.query(PAGE_SQL, params, PLAYER_SUMMARY_ROW_MAPPER);
    }

    public long countPlayerSummariesForBranch(Long organizationId, Long branchId) {
        if (organizationId == null || branchId == null) {
            return 0L;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("organizationId", organizationId)
                .addValue("branchId", branchId);
        Long count = jdbcTemplate.queryForObject(COUNT_SQL, params, Long.class);
        return count == null ? 0L : count;
    }
}
