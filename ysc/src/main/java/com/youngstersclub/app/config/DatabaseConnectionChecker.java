package com.youngstersclub.app.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnectionChecker {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionChecker.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseConnectionChecker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkDatabaseConnection() {
        try {
            jdbcTemplate.execute("SELECT 1");
            log.info("Connected to Supabase PostgreSQL successfully");
        } catch (Exception e) {
            log.error("Database connection failed: {}", e.getMessage(), e);
        }
    }
}
