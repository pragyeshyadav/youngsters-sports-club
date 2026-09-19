package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.ClubNotificationFailureReason;
import com.youngstersclub.app.dto.ClubNotificationFailureReport;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final long FINALIZER_BATCH_SIZE = 100L;
    private static final String SOURCE = "CLUB_NOTIFICATION";

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
        String broadcastId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime deadlineAt = startedAt.plusMinutes(15);
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
            hash.put(stateKey, "deadlineAt", deadlineAt.toString());
            hash.put(stateKey, "acceptedCount", "0");
            hash.put(stateKey, "registrationComplete", "false");
            hash.put(stateKey, "finalized", "false");
            redisTemplate.expire(stateKey, STATE_TTL_MINUTES, TimeUnit.MINUTES);
            redisTemplate.opsForZSet().add(DEADLINE_INDEX_KEY, broadcastId, toEpochMillis(deadlineAt));
            redisTemplate.expire(DEADLINE_INDEX_KEY, STATE_TTL_MINUTES, TimeUnit.MINUTES);
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
                redisTemplate.opsForValue().set(wamidLookupKey(normalizedWamid), broadcastId, STATE_TTL_MINUTES, TimeUnit.MINUTES);
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
            return;
        }
        if ("true".equals(state.get("finalized"))) {
            redisTemplate.opsForZSet().remove(DEADLINE_INDEX_KEY, broadcastId);
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
        if (!deadlineReached && (!registrationComplete || terminalCount < safeWamids.size())) {
            return;
        }

        Boolean claimed = redisTemplate.opsForHash().putIfAbsent(stateKey(broadcastId), "finalized", "true");
        if (!Boolean.TRUE.equals(claimed)) {
            redisTemplate.opsForZSet().remove(DEADLINE_INDEX_KEY, broadcastId);
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
        redisTemplate.expire(stateKey(broadcastId), STATE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.expire(wamidsKey(broadcastId), STATE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.expire(statusesKey(broadcastId), STATE_TTL_MINUTES, TimeUnit.MINUTES);
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
