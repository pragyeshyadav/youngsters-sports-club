package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlayerPerformanceCache {
    private static final Logger log = LoggerFactory.getLogger(PlayerPerformanceCache.class);
    private static final String SNAPSHOT_KEY = "ysc:player-performance:v1:current";
    private static final Duration SNAPSHOT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlayerPerformanceCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<PlayerPerformanceService.Snapshot> read() {
        try {
            String json = redisTemplate.opsForValue().get(SNAPSHOT_KEY);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json,
                    new TypeReference<PlayerPerformanceService.Snapshot>() {}));
        } catch (Exception exception) {
            log.warn("Player performance cache read unavailable. Reason: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public boolean write(PlayerPerformanceService.Snapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(SNAPSHOT_KEY, json, SNAPSHOT_TTL);
            return true;
        } catch (Exception exception) {
            log.warn("Player performance cache write unavailable. Reason: {}", exception.getMessage());
            return false;
        }
    }
}
