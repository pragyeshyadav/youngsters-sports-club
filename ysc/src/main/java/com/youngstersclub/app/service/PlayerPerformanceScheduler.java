package com.youngstersclub.app.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlayerPerformanceScheduler {
    private static final Logger log = LoggerFactory.getLogger(PlayerPerformanceScheduler.class);
    private final PlayerPerformanceService playerPerformanceService;

    public PlayerPerformanceScheduler(PlayerPerformanceService playerPerformanceService) {
        this.playerPerformanceService = playerPerformanceService;
    }

    @Scheduled(
            cron = "${player.performance.snapshot.cron:0 30 11 * * *}",
            zone = "${player.performance.snapshot.zone:Asia/Kolkata}")
    public void refreshSnapshot() {
        try {
            playerPerformanceService.refreshSnapshot();
            log.info("Player performance snapshot refresh completed");
        } catch (Exception exception) {
            log.error("Player performance snapshot refresh failed", exception);
        }
    }
}
