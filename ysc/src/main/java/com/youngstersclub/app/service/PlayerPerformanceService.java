package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.PlayerPerformanceResponseDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerPerformanceService {
    protected static final int MINIMUM_COMPETITIVE_FRAMES = 10;
    private static final int COMPETITOR_WINDOW_SIZE = 5;
    private static final Logger log = LoggerFactory.getLogger(PlayerPerformanceService.class);

    private final FrameRepository frameRepository;
    private final UserRepository userRepository;
    private final PlayerPerformanceCache cache;

    public PlayerPerformanceService(
            FrameRepository frameRepository,
            UserRepository userRepository,
            PlayerPerformanceCache cache) {
        this.frameRepository = frameRepository;
        this.userRepository = userRepository;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public PlayerPerformanceResponseDto getForAuthenticatedUser(String actorEmail) {
        String email = normalize(actorEmail);
        if (email == null) {
            throw new SecurityException("Authenticated user email is required");
        }
        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("Authenticated user was not found"));
        return getForUser(currentUser);
    }

    @Transactional(readOnly = true)
    public PlayerPerformanceResponseDto getForUser(User currentUser) {
        Snapshot snapshot = getOrBuildSnapshot();
        Stat current = snapshot.stats().stream()
                .filter(stat -> currentUser.getId().equals(stat.userId()))
                .findFirst()
                .orElse(zeroStat(currentUser));

        List<PlayerPerformanceResponseDto.CompetitorDto> competitors = buildCompetitorWindow(snapshot.stats(), current);
        boolean eligible = current.totalFrames() >= MINIMUM_COMPETITIVE_FRAMES;
        String message = eligible
                ? null
                : "Play %d more frames to unlock competitor comparison."
                        .formatted(MINIMUM_COMPETITIVE_FRAMES - current.totalFrames());

        return new PlayerPerformanceResponseDto(
                toPlayerDto(current),
                new PlayerPerformanceResponseDto.CompetitorComparisonDto(
                        eligible, MINIMUM_COMPETITIVE_FRAMES, competitors, message),
                snapshot.generatedAt());
    }

    @Transactional(readOnly = true)
    public synchronized Snapshot getOrBuildSnapshot() {
        Optional<Snapshot> cached = cache.read();
        if (cached.isPresent()) {
            return cached.get();
        }

        Snapshot calculated = calculateSnapshot();
        cache.write(calculated);
        return calculated;
    }

    @Transactional(readOnly = true)
    public synchronized Snapshot refreshSnapshot() {
        Snapshot calculated = calculateSnapshot();
        cache.write(calculated);
        return calculated;
    }

    @Transactional(readOnly = true)
    protected Snapshot calculateSnapshot() {
        long startedAt = System.currentTimeMillis();
        Map<Integer, List<String>> recentByUser = new HashMap<>();
        for (FrameRepository.GlobalPlayerRecentFormProjection row : frameRepository.findGlobalPlayerRecentForm()) {
            if (row.getUserId() != null && row.getOutcome() != null) {
                recentByUser.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getOutcome());
            }
        }

        List<Stat> stats = frameRepository.findGlobalPlayerPerformance().stream()
                .filter(row -> row.getUserId() != null)
                .map(row -> new Stat(
                        row.getUserId(),
                        row.getDisplayName(),
                        row.getProfileImageUrl(),
                        safeLong(row.getTotalFrames()),
                        safeLong(row.getWins()),
                        safeLong(row.getLosses()),
                        recentByUser.getOrDefault(row.getUserId(), List.of())))
                .sorted(statComparator())
                .toList();

        Snapshot snapshot = new Snapshot(LocalDateTime.now(), stats);
        log.info("Player performance snapshot generated. eligibleFrames={}, players={}, durationMs={}",
                stats.stream().mapToLong(Stat::totalFrames).sum(), stats.size(),
                System.currentTimeMillis() - startedAt);
        return snapshot;
    }

    protected List<PlayerPerformanceResponseDto.CompetitorDto> buildCompetitorWindow(
            List<Stat> allStats, Stat current) {
        if (current.totalFrames() < MINIMUM_COMPETITIVE_FRAMES) {
            return List.of();
        }
        List<Stat> eligible = allStats.stream()
                .filter(stat -> stat.totalFrames() >= MINIMUM_COMPETITIVE_FRAMES)
                .sorted(statComparator())
                .toList();
        int currentIndex = -1;
        for (int index = 0; index < eligible.size(); index++) {
            if (eligible.get(index).userId().equals(current.userId())) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) {
            return List.of();
        }
        int start = Math.max(0, Math.min(currentIndex - 2, eligible.size() - COMPETITOR_WINDOW_SIZE));
        int end = Math.min(eligible.size(), start + COMPETITOR_WINDOW_SIZE);
        return eligible.subList(start, end).stream().map(stat ->
                new PlayerPerformanceResponseDto.CompetitorDto(
                        stat.displayName(), calculateWinRate(stat.wins(), stat.totalFrames()),
                        stat.totalFrames(), stat.userId().equals(current.userId()))).toList();
    }

    protected Comparator<Stat> statComparator() {
        return Comparator.comparing((Stat stat) -> calculateWinRate(stat.wins(), stat.totalFrames()), Comparator.reverseOrder())
                .thenComparing(Stat::totalFrames, Comparator.reverseOrder())
                .thenComparing(Stat::wins, Comparator.reverseOrder())
                .thenComparing(Stat::userId);
    }

    protected BigDecimal calculateWinRate(long wins, long totalFrames) {
        if (totalFrames <= 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(wins)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalFrames), 1, RoundingMode.HALF_UP);
    }

    protected Stat zeroStat(User user) {
        return new Stat(user.getId(), user.getName(), user.getProfilePic(), 0, 0, 0, List.of());
    }

    protected PlayerPerformanceResponseDto.PlayerStatsDto toPlayerDto(Stat stat) {
        return new PlayerPerformanceResponseDto.PlayerStatsDto(
                stat.displayName(), stat.profileImageUrl(), stat.totalFrames(), stat.wins(), stat.losses(),
                calculateWinRate(stat.wins(), stat.totalFrames()), stat.recentForm());
    }

    protected String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    public record Snapshot(LocalDateTime generatedAt, List<Stat> stats) {}

    public record Stat(
            Integer userId,
            String displayName,
            String profileImageUrl,
            long totalFrames,
            long wins,
            long losses,
            List<String> recentForm) {}
}
