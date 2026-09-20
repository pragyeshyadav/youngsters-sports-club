package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.ClubNotificationFailureReason;
import com.youngstersclub.app.dto.ClubNotificationFailureReport;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Tracks only Club Notification broadcasts. The normal per-message WhatsApp
 * status store remains the source for the existing status screen.
 */
@Service
public class ClubNotificationBroadcastTracker {

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationBroadcastTracker.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final String PREFIX = "ysc:whatsapp:club-broadcast:v1:";
    private static final String DEADLINE_INDEX_KEY = PREFIX + "deadlines";
    private static final long STATE_TTL_MINUTES = 60L;
    private static final long RETENTION_BUFFER_MINUTES = 30L;
    private static final long FINALIZER_BATCH_SIZE = 100L;
    private static final String SOURCE = "CLUB_NOTIFICATION";
    private static final DefaultRedisScript<Long> CLAIM_FINALIZATION_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "if current == ARGV[2] then "
                    + "redis.call('HSET', KEYS[1], ARGV[1], ARGV[3]) "
                    + "return 1 "
                    + "end "
                    + "return 0",
            Long.class);
    private static final DefaultRedisScript<Long> EXTEND_DEADLINE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'finalized') == 'true' then return 0 end "
                    + "local current = tonumber(redis.call('HGET', KEYS[1], 'deadlineEpoch')) or 0 "
                    + "if current >= tonumber(ARGV[1]) then "
                    + "redis.call('ZADD', KEYS[2], current, ARGV[5]) return 1 end "
                    + "redis.call('HSET', KEYS[1], 'deadlineAt', ARGV[2], 'deadlineEpoch', ARGV[1], 'expiryEpoch', ARGV[3], 'ttlSeconds', ARGV[4]) "
                    + "redis.call('ZADD', KEYS[2], ARGV[1], ARGV[5]) "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[4]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ClubNotificationFailureEmailService failureEmailService;

    @Autowired
    public ClubNotificationBroadcastTracker(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ClubNotificationFailureEmailService failureEmailService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.failureEmailService = failureEmailService;
    }

    public String startBroadcast(
            Long organizationId,
            Long branchId,
            String organizationName,
            String templateName,
            String notificationMessage) {
        return startBroadcast(
                organizationId,
                branchId,
                organizationName,
                templateName,
                notificationMessage,
                LocalDateTime.now(BUSINESS_ZONE).plusMinutes(15));
    }

    public String startBroadcast(
            Long organizationId,
            Long branchId,
            String organizationName,
            String templateName,
            String notificationMessage,
            LocalDateTime deadlineAt) {
        String broadcastId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime safeDeadlineAt = deadlineAt == null ? startedAt.plusMinutes(15) : deadlineAt;
        LocalDateTime expiryAt = safeDeadlineAt.plusMinutes(RETENTION_BUFFER_MINUTES);
        long ttlSeconds = Math.max(60L, Duration.between(Instant.now(), expiryAt.atZone(BUSINESS_ZONE).toInstant()).getSeconds());
        String stateKey = stateKey(broadcastId);
        try {
            HashOperations<String, String, String> hash = redisTemplate.opsForHash();
            hash.put(stateKey, "source", SOURCE);
            hash.put(stateKey, "organizationId", String.valueOf(organizationId));
            hash.put(stateKey, "branchId", branchId == null ? "" : String.valueOf(branchId));
            hash.put(stateKey, "organizationName", safe(organizationName));
            hash.put(stateKey, "templateName", safe(templateName));
            hash.put(stateKey, "notificationMessage", safe(notificationMessage));
            hash.put(stateKey, "triggeredAt", startedAt.toString());
            hash.put(stateKey, "deadlineAt", safeDeadlineAt.toString());
            hash.put(stateKey, "deadlineEpoch", String.valueOf(toEpochMillis(safeDeadlineAt)));
            hash.put(stateKey, "expiryEpoch", String.valueOf(toEpochMillis(expiryAt)));
            hash.put(stateKey, "ttlSeconds", String.valueOf(ttlSeconds));
            hash.put(stateKey, "lifecycle", "CREATING");
            hash.put(stateKey, "acceptedCount", "0");
            hash.put(stateKey, "registrationComplete", "false");
            hash.put(stateKey, "finalized", "false");
            redisTemplate.expire(stateKey, ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.opsForZSet().add(DEADLINE_INDEX_KEY, broadcastId, toEpochMillis(safeDeadlineAt));
            log.info("Club notification broadcast tracking started. broadcastId: {}, organizationId: {}", broadcastId, organizationId);
        } catch (Exception ex) {
            log.warn("Unable to start Club Notification broadcast tracking. organizationId: {}, reason: {}", organizationId, ex.getMessage());
        }
        return broadcastId;
    }

    public void registerAccepted(String broadcastId, String wamid) {
        if (isBlank(broadcastId) || isBlank(wamid)) {
            return;
        }
        try {
            String normalizedWamid = wamid.trim();
            Boolean added = redisTemplate.opsForSet().add(wamidsKey(broadcastId), normalizedWamid) == 1L;
            if (Boolean.TRUE.equals(added)) {
                redisTemplate.opsForHash().increment(stateKey(broadcastId), "acceptedCount", 1L);
                redisTemplate.opsForValue().set(
                        wamidLookupKey(normalizedWamid),
                        broadcastId,
                        remainingTtlSeconds(broadcastId),
                        TimeUnit.SECONDS);
                expireRelatedKeys(broadcastId);
            }
        } catch (Exception ex) {
            log.warn("Unable to register Club Notification wamid. broadcastId: {}, reason: {}", broadcastId, ex.getMessage());
        }
    }

    public void completeRegistration(String broadcastId) {
        if (isBlank(broadcastId)) {
            return;
        }
        try {
            redisTemplate.opsForHash().put(stateKey(broadcastId), "registrationComplete", "true");
            redisTemplate.opsForHash().put(stateKey(broadcastId), "lifecycle", "READY");
            finalizeIfComplete(broadcastId, false);
        } catch (Exception ex) {
            log.warn("Unable to complete Club Notification registration. broadcastId: {}, reason: {}", broadcastId, ex.getMessage());
        }
    }

    public void recordWebhookStatus(String wamid, JsonNode statusNode) {
        if (isBlank(wamid) || statusNode == null) {
            return;
        }
        try {
            String broadcastId = redisTemplate.opsForValue().get(wamidLookupKey(wamid));
            if (isBlank(broadcastId)) {
                return;
            }
            String incomingStatus = normalize(statusNode.path("status").asText(null));
            if (isBlank(incomingStatus)) {
                return;
            }

            String statusesKey = statusesKey(broadcastId);
            Object previousValue = redisTemplate.opsForHash().get(statusesKey, wamid);
            String previousJson = previousValue == null ? null : String.valueOf(previousValue);
            String previousStatus = previousJson == null ? null : readStatus(previousJson);
            if (!shouldReplaceStatus(previousStatus, incomingStatus)) {
                return;
            }

            WebhookStatus status = new WebhookStatus(incomingStatus.toUpperCase(), extractErrors(statusNode));
            redisTemplate.opsForHash().put(statusesKey, wamid, objectMapper.writeValueAsString(status));
            expireRelatedKeys(broadcastId);
            log.info("Club notification webhook status captured. broadcastId: {}, status: {}", broadcastId, status.status());
            finalizeIfComplete(broadcastId, false);
        } catch (Exception ex) {
            log.warn("Unable to capture Club Notification webhook status. wamid: {}, reason: {}", wamid, ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${whatsapp.club-notification.failure-finalizer-delay-ms:30000}")
    public void finalizeExpiredBroadcasts() {
        try {
            Set<String> dueBroadcasts = redisTemplate.opsForZSet().rangeByScore(
                    DEADLINE_INDEX_KEY,
                    0,
                    System.currentTimeMillis(),
                    0,
                    FINALIZER_BATCH_SIZE);
            if (dueBroadcasts == null) {
                return;
            }
            for (String broadcastId : dueBroadcasts) {
                finalizeIfComplete(broadcastId, true);
            }
        } catch (Exception ex) {
            log.warn("Unable to finalize expired Club Notification broadcasts. reason: {}", ex.getMessage());
        }
    }

    protected void finalizeIfComplete(String broadcastId, boolean timedOut) {
        Map<Object, Object> state = redisTemplate.opsForHash().entries(stateKey(broadcastId));
        if (state == null || state.isEmpty() || !SOURCE.equals(state.get("source"))) {
            redisTemplate.opsForZSet().remove(DEADLINE_INDEX_KEY, broadcastId);
            return;
        }
        if ("true".equals(state.get("finalized"))) {
            return;
        }

        Set<String> acceptedWamids = redisTemplate.opsForSet().members(wamidsKey(broadcastId));
        Set<String> safeWamids = acceptedWamids == null ? Set.of() : acceptedWamids;
        Map<Object, Object> statusEntries = redisTemplate.opsForHash().entries(statusesKey(broadcastId));
        Map<String, String> statuses = new HashMap<>();
        if (statusEntries != null) {
            statusEntries.forEach((key, value) -> statuses.put(String.valueOf(key), String.valueOf(value)));
        }

        int terminalCount = 0;
        int failedCount = 0;
        Map<String, MutableFailureReason> failures = new HashMap<>();
        for (String wamid : safeWamids) {
            String statusJson = statuses.get(wamid);
            String status = readStatus(statusJson);
            if (isTerminal(status)) {
                terminalCount++;
            }
            if ("FAILED".equals(status)) {
                failedCount++;
                for (FailureError error : readErrors(statusJson)) {
                    String signature = error.signature();
                    failures.computeIfAbsent(signature, ignored -> new MutableFailureReason(error)).affectedCount++;
                }
            }
        }

        boolean registrationComplete = "true".equals(state.get("registrationComplete"));
        boolean deadlineReached = timedOut || isDeadlineReached(state.get("deadlineAt"));
        // A deadline is only meaningful after every batch has finished adding
        // accepted wamids. Otherwise an early batch could be finalized while
        // later batches are still waiting to be sent.
        if (!registrationComplete || (!deadlineReached && terminalCount < safeWamids.size())) {
            return;
        }

        if (!claimFinalization(broadcastId)) {
            // Another instance owns finalization, or the state is still open.
            // Leave deadline cleanup to the caller that wins the CAS.
            return;
        }

        int pendingCount = Math.max(0, safeWamids.size() - terminalCount);
        List<ClubNotificationFailureReason> failureReasons = failures.values().stream()
                .map(MutableFailureReason::toDto)
                .sorted(Comparator.comparingInt(ClubNotificationFailureReason::affectedCount).reversed()
                        .thenComparing(reason -> safe(reason.code()))
                        .thenComparing(reason -> safe(reason.title()))
                        .thenComparing(reason -> safe(reason.message()))
                        .thenComparing(reason -> safe(reason.details())))
                .toList();
        LocalDateTime finalizedAt = LocalDateTime.now(BUSINESS_ZONE);
        ClubNotificationFailureReport report = new ClubNotificationFailureReport(
                broadcastId,
                parseLong(state.get("organizationId")),
                safe(state.get("organizationName")),
                safe(state.get("templateName")),
                safe(state.get("notificationMessage")),
                parseDateTime(state.get("triggeredAt")),
                finalizedAt,
                safeWamids.size(),
                failedCount,
                Math.max(0, terminalCount - failedCount),
                pendingCount,
                deadlineReached && pendingCount > 0,
                failureReasons);
        redisTemplate.opsForHash().put(stateKey(broadcastId), "finalizedAt", finalizedAt.toString());
        redisTemplate.opsForZSet().remove(DEADLINE_INDEX_KEY, broadcastId);
        log.info(
                "Club notification broadcast finalized. broadcastId: {}, accepted: {}, failed: {}, pending: {}, distinctFailureReasons: {}, timeout: {}",
                broadcastId,
                report.acceptedCount(),
                report.failedCount(),
                report.pendingCount(),
                report.failureReasons().size(),
                report.timedOut());
        if (failedCount > 0) {
            failureEmailService.sendAsync(report);
        }
    }

    public boolean ensureDeadlineAtLeast(String broadcastId, LocalDateTime desiredDeadlineAt) {
        if (isBlank(broadcastId) || desiredDeadlineAt == null) {
            return false;
        }
        try {
            LocalDateTime expiryAt = desiredDeadlineAt.plusMinutes(RETENTION_BUFFER_MINUTES);
            long expiryEpoch = toEpochMillis(expiryAt);
            long ttlSeconds = Math.max(60L, Duration.between(Instant.now(), expiryAt.atZone(BUSINESS_ZONE).toInstant()).getSeconds());
            Long changed = redisTemplate.execute(
                    EXTEND_DEADLINE_SCRIPT,
                    List.of(stateKey(broadcastId), DEADLINE_INDEX_KEY),
                    String.valueOf(toEpochMillis(desiredDeadlineAt)),
                    desiredDeadlineAt.toString(),
                    String.valueOf(expiryEpoch),
                    String.valueOf(ttlSeconds),
                    broadcastId);
            if (Long.valueOf(1L).equals(changed)) {
                refreshAcceptedWamidTtls(broadcastId, ttlSeconds);
                return true;
            }
            return false;
        } catch (Exception ex) {
            log.warn("Unable to extend Club Notification failure deadline. broadcastId: {}, reason: {}",
                    broadcastId, ex.getMessage());
            return false;
        }
    }

    public void markReady(String broadcastId) {
        if (isBlank(broadcastId)) {
            return;
        }
        redisTemplate.opsForHash().put(stateKey(broadcastId), "lifecycle", "READY");
    }

    public void abortBroadcast(String broadcastId) {
        if (isBlank(broadcastId)) {
            return;
        }
        redisTemplate.opsForHash().put(stateKey(broadcastId), "lifecycle", "ABORTED");
        redisTemplate.opsForZSet().remove(DEADLINE_INDEX_KEY, broadcastId);
    }

    /**
     * Atomically claims the single finalization slot shared by all instances.
     */
    protected boolean claimFinalization(String broadcastId) {
        Long result = redisTemplate.execute(
                CLAIM_FINALIZATION_SCRIPT,
                List.of(stateKey(broadcastId)),
                "finalized",
                "false",
                "true");
        return Long.valueOf(1L).equals(result);
    }

    protected boolean shouldReplaceStatus(String previousStatus, String incomingStatus) {
        if (previousStatus == null || previousStatus.isBlank()) {
            return true;
        }
        if ("FAILED".equalsIgnoreCase(previousStatus)) {
            return false;
        }
        if ("FAILED".equalsIgnoreCase(incomingStatus)) {
            return true;
        }
        return statusRank(incomingStatus) >= statusRank(previousStatus);
    }

    protected int statusRank(String status) {
        return switch (normalize(status).toUpperCase()) {
            case "SENT" -> 1;
            case "DELIVERED" -> 2;
            case "READ" -> 3;
            case "FAILED" -> 4;
            default -> 0;
        };
    }

    private List<FailureError> extractErrors(JsonNode statusNode) {
        List<FailureError> errors = new ArrayList<>();
        JsonNode errorsNode = statusNode.path("errors");
        if (errorsNode.isArray()) {
            for (JsonNode errorNode : errorsNode) {
                errors.add(new FailureError(
                        errorNode.path("code").isIntegralNumber() ? errorNode.path("code").asInt() : null,
                        text(errorNode, "title"),
                        text(errorNode, "message"),
                        text(errorNode.path("error_data"), "details")));
            }
        }
        return errors;
    }

    private List<FailureError> readErrors(String json) {
        if (json == null) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(json).path("errors");
            List<FailureError> errors = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode error : root) {
                    errors.add(new FailureError(
                            error.path("code").isIntegralNumber() ? error.path("code").asInt() : null,
                            text(error, "title"), text(error, "message"), text(error, "details")));
                }
            }
            return errors;
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String readStatus(String json) {
        if (json == null) {
            return null;
        }
        try {
            return normalize(objectMapper.readTree(json).path("status").asText(null)).toUpperCase();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isTerminal(String status) {
        return "FAILED".equals(status) || "DELIVERED".equals(status) || "READ".equals(status);
    }

    private boolean isDeadlineReached(Object rawDeadline) {
        LocalDateTime deadline = parseDateTime(rawDeadline);
        return deadline != null && !LocalDateTime.now(BUSINESS_ZONE).isBefore(deadline);
    }

    private void expireRelatedKeys(String broadcastId) {
        long ttlSeconds = remainingTtlSeconds(broadcastId);
        redisTemplate.expire(stateKey(broadcastId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(wamidsKey(broadcastId), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(statusesKey(broadcastId), ttlSeconds, TimeUnit.SECONDS);
    }

    private long remainingTtlSeconds(String broadcastId) {
        Object expiry = redisTemplate.opsForHash().get(stateKey(broadcastId), "expiryEpoch");
        try {
            return Math.max(1L, (Long.parseLong(String.valueOf(expiry)) - System.currentTimeMillis()) / 1000L);
        } catch (Exception ex) {
            return STATE_TTL_MINUTES * 60L;
        }
    }

    private void refreshAcceptedWamidTtls(String broadcastId, long ttlSeconds) {
        Set<String> wamids = redisTemplate.opsForSet().members(wamidsKey(broadcastId));
        if (wamids == null) return;
        for (String wamid : wamids) {
            redisTemplate.expire(wamidLookupKey(wamid), ttlSeconds, TimeUnit.SECONDS);
        }
    }

    private String stateKey(String broadcastId) { return PREFIX + broadcastId + ":state"; }
    private String wamidsKey(String broadcastId) { return PREFIX + broadcastId + ":wamids"; }
    private String statusesKey(String broadcastId) { return PREFIX + broadcastId + ":statuses"; }
    private String wamidLookupKey(String wamid) { return PREFIX + "wamid:" + wamid; }
    private long toEpochMillis(LocalDateTime value) { return value.atZone(BUSINESS_ZONE).toInstant().toEpochMilli(); }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        try { return LocalDateTime.parse(String.valueOf(value)); } catch (Exception ex) { return null; }
    }

    private Long parseLong(Object value) {
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); } catch (Exception ex) { return null; }
    }

    private String text(JsonNode node, String field) {
        return node == null ? null : normalize(node.path(field).asText(null));
    }

    private String normalize(String value) { return value == null ? "" : value.trim(); }
    private String safe(String value) { return value == null ? "" : value; }
    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }

    private record WebhookStatus(String status, List<FailureError> errors) {}
    private record FailureError(Integer code, String title, String message, String details) {
        private String signature() { return String.valueOf(code) + "|" + String.valueOf(title) + "|" + String.valueOf(message) + "|" + String.valueOf(details); }
    }

    private static final class MutableFailureReason {
        private final FailureError error;
        private int affectedCount;
        private MutableFailureReason(FailureError error) { this.error = error; }
        private ClubNotificationFailureReason toDto() {
            return new ClubNotificationFailureReason(error.code(), error.title(), error.message(), error.details(), affectedCount);
        }
    }
}
