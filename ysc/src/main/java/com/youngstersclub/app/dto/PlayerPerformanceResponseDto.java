package com.youngstersclub.app.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PlayerPerformanceResponseDto(
        PlayerStatsDto player,
        CompetitorComparisonDto competitorComparison,
        LocalDateTime lastUpdatedAt) {

    public record PlayerStatsDto(
            String displayName,
            String profileImageUrl,
            long totalFrames,
            long wins,
            long losses,
            BigDecimal winRate,
            List<String> recentForm) {}

    public record CompetitorComparisonDto(
            boolean eligible,
            int minimumFramesRequired,
            List<CompetitorDto> players,
            String message) {}

    public record CompetitorDto(
            String displayName,
            BigDecimal winRate,
            long totalFrames,
            boolean currentPlayer) {}
}
