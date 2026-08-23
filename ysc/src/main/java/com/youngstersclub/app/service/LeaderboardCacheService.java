package com.youngstersclub.app.service;

import static com.youngstersclub.app.config.CacheConfig.MONTHLY_LEADERBOARD_CACHE;

import com.youngstersclub.app.repository.FrameRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class LeaderboardCacheService {

    private final FrameRepository frameRepository;

    public LeaderboardCacheService(FrameRepository frameRepository) {
        this.frameRepository = frameRepository;
    }

    @Cacheable(
            cacheNames = MONTHLY_LEADERBOARD_CACHE,
            key = "'leaderboard:' + #branchId + ':' + #year + ':' + #month")
    public List<Map<String, Object>> getTopPlayersForBranchMonth(
            Long branchId,
            String branchName,
            Integer year,
            Integer month,
            LocalDateTime startInclusive,
            LocalDateTime endExclusive) {
        return frameRepository.findTopPlayersOfMonthByBranch(branchId, startInclusive, endExclusive)
                .stream()
                .map(projection -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", projection.getUserId());
                    map.put("name", projection.getName());
                    map.put("wins", projection.getWins());
                    map.put("branchId", branchId);
                    map.put("branchName", branchName);
                    map.put("year", year);
                    map.put("month", month);
                    return map;
                })
                .toList();
    }
}
