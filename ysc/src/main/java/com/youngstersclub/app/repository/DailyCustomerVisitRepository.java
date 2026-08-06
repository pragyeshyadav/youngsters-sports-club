package com.youngstersclub.app.repository;

import com.youngstersclub.app.dto.DailyVisitedOrganizationDto;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyCustomerVisitRepository {

    private static final String FIND_DAILY_VISITS_BY_ORGANIZATION_SQL = """
            SELECT
                visited.user_id AS userId,
                visited.name AS name,
                visited.phone AS phone,
                visited.organization_id AS organizationId,
                visited.organization_name AS organizationName,
                NULL::bigint AS branchId,
                COALESCE(
                    STRING_AGG(DISTINCT visited.branch_name, ', ' ORDER BY visited.branch_name)
                        FILTER (WHERE visited.branch_name IS NOT NULL AND visited.branch_name <> ''),
                    'Organization-wide'
                ) AS branchName
            FROM (
                SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
                FROM frames f
                JOIN frame_players fp ON fp.frame_id = f.id
                JOIN users u ON u.id = fp.user_id
                JOIN branches b ON b.id = f.branch_id
                JOIN organizations o ON o.id = b.organization_id
                JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
                WHERE DATE(f.start_time) = :selectedDate
                  AND COALESCE(u.is_active, true) = true
                  AND COALESCE(ou.is_active, true) = true
                  AND ou.role = 'CUSTOMER'

                UNION

                SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
                FROM consumable_orders co
                JOIN users u ON u.id = co.user_id
                JOIN branches b ON b.id = co.branch_id
                JOIN organizations o ON o.id = b.organization_id
                JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
                WHERE DATE(co.created_at) = :selectedDate
                  AND COALESCE(u.is_active, true) = true
                  AND COALESCE(ou.is_active, true) = true
                  AND ou.role = 'CUSTOMER'

                UNION

                SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
                FROM kids_play_sessions kps
                JOIN users u ON u.id = kps.parent_user_id
                JOIN branches b ON b.id = kps.branch_id
                JOIN organizations o ON o.id = b.organization_id
                JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
                WHERE DATE(kps.start_time) = :selectedDate
                  AND COALESCE(u.is_active, true) = true
                  AND COALESCE(ou.is_active, true) = true
                  AND ou.role = 'CUSTOMER'

                UNION

                SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
                FROM game_activity_orders gao
                JOIN users u ON u.id = gao.parent_user_id
                JOIN branches b ON b.id = gao.branch_id
                JOIN organizations o ON o.id = b.organization_id
                JOIN organization_users ou ON ou.user_id = u.id AND ou.organization_id = o.id
                WHERE DATE(gao.created_at) = :selectedDate
                  AND COALESCE(u.is_active, true) = true
                  AND COALESCE(ou.is_active, true) = true
                  AND ou.role = 'CUSTOMER'

                UNION

                SELECT u.id AS user_id, u.name, u.phone, o.id AS organization_id, o.name AS organization_name, b.id AS branch_id, b.name AS branch_name
                FROM organization_users ou
                JOIN users u ON u.id = ou.user_id
                JOIN organizations o ON o.id = ou.organization_id
                LEFT JOIN branches b ON b.id = ou.base_branch_id
                WHERE DATE(ou.created_at) = :selectedDate
                  AND COALESCE(u.is_active, true) = true
                  AND COALESCE(ou.is_active, true) = true
                  AND ou.role = 'CUSTOMER'
            ) visited
            GROUP BY
                visited.user_id,
                visited.name,
                visited.phone,
                visited.organization_id,
                visited.organization_name
            ORDER BY visited.organization_name ASC, visited.name ASC
            """;

    private static final RowMapper<DailyVisitedOrganizationDto> DAILY_VISIT_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public DailyVisitedOrganizationDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new DailyVisitedOrganizationDto(
                            rs.getInt("userId"),
                            rs.getString("name"),
                            rs.getString("phone"),
                            rs.getLong("organizationId"),
                            rs.getString("organizationName"),
                            getNullableLong(rs, "branchId"),
                            rs.getString("branchName"));
                }
            };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DailyCustomerVisitRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DailyVisitedOrganizationDto> findDailyVisitedCustomersByOrganization(LocalDate selectedDate) {
        return jdbcTemplate.query(
                FIND_DAILY_VISITS_BY_ORGANIZATION_SQL,
                new MapSqlParameterSource("selectedDate", selectedDate),
                DAILY_VISIT_ROW_MAPPER);
    }

    private static Long getNullableLong(ResultSet rs, String columnLabel) throws SQLException {
        long value = rs.getLong(columnLabel);
        return rs.wasNull() ? null : value;
    }
}
