package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.ClubNotificationBroadcastSummary;
import com.youngstersclub.app.entity.User;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Valkey-backed dispatcher for Club Notification batches only. */
@Service
public class ClubNotificationBatchDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationBatchDispatcher.class);
    private static final String PREFIX = "ysc:whatsapp:club-broadcast:v1:";
    private static final String BATCH_DEADLINES_KEY = PREFIX + "batch-deadlines";
    private static final String PENDING = "PENDING";
    private static final String CLAIMED = "CLAIMED";
    private static final String COMPLETED = "COMPLETED";
    private static final String SEND_ATTEMPTED = "SEND_ATTEMPTED";
    private static final String META_ACCEPTED = "META_ACCEPTED";
    private static final String SEND_FAILED = "SEND_FAILED";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final long SAFETY_BUFFER_MINUTES = 30L;
    private static final DefaultRedisScript<Long> CLAIM_BATCH_SCRIPT = new DefaultRedisScript<>(
            "local status = redis.call('HGET', KEYS[1], 'status') "
                    + "if status == 'COMPLETED' then return 0 end "
                    + "local lease = tonumber(redis.call('HGET', KEYS[1], 'leaseUntilEpoch')) or 0 "
                    + "if status == 'CLAIMED' and lease > tonumber(ARGV[1]) then return 0 end "
                    + "if status == 'PENDING' or status == 'CLAIMED' then "
                    + "redis.call('HSET', KEYS[1], 'status', 'CLAIMED', 'leaseUntilEpoch', ARGV[2]) "
                    + "return 1 end return 0",
            Long.class);
    private static final DefaultRedisScript<Long> CLAIM_SUMMARY_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], 'summarySent') == 'false' then "
                    + "redis.call('HSET', KEYS[1], 'summarySent', 'true') return 1 end return 0",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final WhatsAppService whatsAppService;
    private final ClubNotificationBroadcastTracker tracker;
    private final BrevoEmailService brevoEmailService;
    private final OrganizationSummaryRecipientService recipientService;
    private final int batchSize;
    private final long batchIntervalMinutes;
    private final long webhookGraceMinutes;

    public ClubNotificationBatchDispatcher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            WhatsAppService whatsAppService,
            ClubNotificationBroadcastTracker tracker,
            BrevoEmailService brevoEmailService,
            OrganizationSummaryRecipientService recipientService,
            @Value("${whatsapp.club-notification.batch-size:100}") int batchSize,
            @Value("${whatsapp.club-notification.batch-interval-minutes:15}") long batchIntervalMinutes,
            @Value("${whatsapp.club-notification.failure-grace-period-minutes:15}") long webhookGraceMinutes) {
        if (batchSize <= 0 || batchIntervalMinutes <= 0 || webhookGraceMinutes <= 0) {
            throw new IllegalArgumentException("Club Notification batching values must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.whatsAppService = whatsAppService;
        this.tracker = tracker;
        this.brevoEmailService = brevoEmailService;
        this.recipientService = recipientService;
        this.batchSize = batchSize;
        this.batchIntervalMinutes = batchIntervalMinutes;
        this.webhookGraceMinutes = webhookGraceMinutes;
    }

    public String startBroadcast(
            List<User> eligibleRecipients,
            int originalCandidateCount,
            int healthExcludedCount,
            String message,
            String recipientTypeLabel,
            Long organizationId,
            Long branchId,
            String branchLabel,
            String organizationName,
            String organizationPhone,
            String organizationEmail) {
        List<User> safeRecipients = eligibleRecipients == null ? List.of() : List.copyOf(eligibleRecipients);
        if (safeRecipients.isEmpty()) {
            log.info("Club Notification has no eligible recipients. organizationId: {}, candidates: {}, healthExcluded: {}",
                    organizationId, originalCandidateCount, healthExcludedCount);
            return null;
        }

        LocalDateTime triggeredAt = LocalDateTime.now(BUSINESS_ZONE);
        int batchCount = (safeRecipients.size() + batchSize - 1) / batchSize;
        LocalDateTime originalDeadline = triggeredAt
                .plusMinutes((long) (batchCount - 1) * batchIntervalMinutes + webhookGraceMinutes);
        String broadcastId = tracker.startBroadcast(
                organizationId,
                branchId,
                organizationName,
                "club_customer_notification_org_wise",
                message,
                originalDeadline);
        long expiryEpoch = toEpochMillis(originalDeadline.plusMinutes(SAFETY_BUFFER_MINUTES));
        long ttlSeconds = Math.max(60L, (expiryEpoch - System.currentTimeMillis()) / 1000L);
        String planKey = planKey(broadcastId);
        try {
            putPlan(planKey, originalCandidateCount, healthExcludedCount, safeRecipients.size(),
                    batchCount, message, recipientTypeLabel, organizationId, branchId, branchLabel,
                    organizationName, organizationPhone, organizationEmail, originalDeadline, expiryEpoch,
                    "CREATING");
            for (int index = 0; index < batchCount; index++) {
                int from = index * batchSize;
                int to = Math.min(from + batchSize, safeRecipients.size());
                LocalDateTime scheduledAt = triggeredAt.plusMinutes((long) index * batchIntervalMinutes);
                createBatch(broadcastId, index, safeRecipients.subList(from, to), scheduledAt, ttlSeconds);
            }
            redisTemplate.opsForHash().put(planKey, "lifecycle", "READY");
            tracker.markReady(broadcastId);
            dispatchDueBatches();
            return broadcastId;
        } catch (Exception ex) {
            log.error("Unable to persist Club Notification batches. broadcastId: {}, reason: {}", broadcastId, ex.getMessage(), ex);
            redisTemplate.opsForHash().put(planKey, "lifecycle", "ABORTED");
            for (int index = 0; index < batchCount; index++) {
                redisTemplate.opsForZSet().remove(BATCH_DEADLINES_KEY, broadcastId + ":" + index);
            }
            tracker.abortBroadcast(broadcastId);
            return broadcastId;
        }
    }

    @Scheduled(fixedDelayString = "${whatsapp.club-notification.failure-finalizer-delay-ms:30000}")
    public void dispatchDueBatches() {
        if (redisTemplate == null) return;
        Set<String> due = redisTemplate.opsForZSet().rangeByScore(BATCH_DEADLINES_KEY, 0, System.currentTimeMillis(), 0, 100);
        if (due == null) return;
        for (String member : due) {
            String[] parts = member.split(":");
            if (parts.length != 2) continue;
            try {
                processBatch(parts[0], Integer.parseInt(parts[1]));
            } catch (Exception ex) {
                log.error("Club Notification batch processing failed. batch: {}, reason: {}", member, ex.getMessage(), ex);
            }
        }
    }

    protected void processBatch(String broadcastId, int batchNumber) {
        Plan plan = loadPlan(broadcastId);
        if (plan == null) {
            redisTemplate.opsForZSet().remove(BATCH_DEADLINES_KEY, broadcastId + ":" + batchNumber);
            return;
        }
        if (!"READY".equals(plan.lifecycle())) {
            if ("ABORTED".equals(plan.lifecycle())) {
                redisTemplate.opsForZSet().remove(BATCH_DEADLINES_KEY, broadcastId + ":" + batchNumber);
            }
            return;
        }

        String stateKey = batchStateKey(broadcastId, batchNumber);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(stateKey))) {
            redisTemplate.opsForZSet().remove(BATCH_DEADLINES_KEY, broadcastId + ":" + batchNumber);
            return;
        }
        long now = System.currentTimeMillis();
        Long claimed = redisTemplate.execute(
                CLAIM_BATCH_SCRIPT,
                List.of(stateKey),
                String.valueOf(now),
                String.valueOf(now + TimeUnit.MINUTES.toMillis(20)));
        if (!Long.valueOf(1L).equals(claimed)) return;

        Map<Object, Object> recipients = redisTemplate.opsForHash().entries(batchRecipientsKey(broadcastId, batchNumber));
        List<String> indexes = recipients.keySet().stream().map(String::valueOf).sorted(Comparator.comparingInt(Integer::parseInt)).toList();
        for (String index : indexes) {
            BatchRecipient recipient = readRecipient(String.valueOf(recipients.get(index)));
            if (recipient == null || !PENDING.equals(recipient.state())) continue;
            BatchRecipient attempted = recipient.withState(SEND_ATTEMPTED, null);
            writeRecipient(broadcastId, batchNumber, index, attempted);
            try {
                WhatsAppService.ClubNotificationSendResult result = whatsAppService.sendClubCustomerNotificationMessageForBroadcast(
                        recipient.phone(),
                        recipient.name(),
                        plan.message(),
                        plan.organizationPhone(),
                        plan.organizationName(),
                        plan.organizationId(),
                        plan.branchId(),
                        plan.branchLabel(),
                        recipient.userId(),
                        wamid -> {
                            writeRecipient(broadcastId, batchNumber, index, attempted.withState(META_ACCEPTED, wamid));
                            tracker.registerAccepted(broadcastId, wamid);
                        });
                if (result != null && result.accepted() && result.wamid() != null) {
                    writeRecipient(broadcastId, batchNumber, index, attempted.withState(META_ACCEPTED, result.wamid()));
                    tracker.registerAccepted(broadcastId, result.wamid());
                } else {
                    writeRecipient(broadcastId, batchNumber, index, attempted.withState(SEND_FAILED, null));
                }
            } catch (Exception ex) {
                log.warn("Club Notification recipient send became uncertain. broadcastId: {}, batch: {}, recipientIndex: {}, reason: {}",
                        broadcastId, batchNumber, index, ex.getMessage());
            }
        }

        if (batchNumber == plan.batchCount() - 1) {
            LocalDateTime completedAt = LocalDateTime.now(BUSINESS_ZONE);
            LocalDateTime finalDeadline = completedAt.plusMinutes(webhookGraceMinutes);
            if (!tracker.ensureDeadlineAtLeast(broadcastId, finalDeadline)) {
                requeueBatch(broadcastId, batchNumber);
                return;
            }
            refreshPlanRetention(broadcastId, plan, finalDeadline.plusMinutes(SAFETY_BUFFER_MINUTES));
        }
        redisTemplate.opsForHash().put(stateKey, "status", COMPLETED);
        redisTemplate.opsForHash().put(stateKey, "completedAt", LocalDateTime.now(BUSINESS_ZONE).toString());
        redisTemplate.opsForZSet().remove(BATCH_DEADLINES_KEY, broadcastId + ":" + batchNumber);
        if (batchNumber == plan.batchCount() - 1) {
            tracker.completeRegistration(broadcastId);
            sendSummaryOnce(broadcastId, plan);
        }
    }

    protected void sendSummaryOnce(String broadcastId, Plan plan) {
        Long claimed = redisTemplate.execute(CLAIM_SUMMARY_SCRIPT, List.of(planKey(broadcastId)));
        if (!Long.valueOf(1L).equals(claimed)) return;
        List<User> users = new ArrayList<>();
        int attempted = 0;
        int accepted = 0;
        int immediateFailures = 0;
        int uncertain = 0;
        for (int batch = 0; batch < plan.batchCount(); batch++) {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(batchRecipientsKey(broadcastId, batch));
            for (Object value : entries.values()) {
                BatchRecipient recipient = readRecipient(String.valueOf(value));
                if (recipient == null) continue;
                User user = new User();
                user.setId(recipient.userId());
                user.setName(recipient.name());
                user.setPhone(recipient.phone());
                users.add(user);
                if (!PENDING.equals(recipient.state())) attempted++;
                if (META_ACCEPTED.equals(recipient.state())) accepted++;
                if (SEND_FAILED.equals(recipient.state())) immediateFailures++;
                if (SEND_ATTEMPTED.equals(recipient.state())) uncertain++;
            }
        }
        ClubNotificationBroadcastSummary summary = new ClubNotificationBroadcastSummary(
                plan.recipientTypeLabel(),
                plan.message(),
                plan.originalCandidates(),
                plan.healthExcludedCount(),
                plan.eligibleCount(),
                attempted,
                accepted,
                immediateFailures,
                uncertain);
        try {
            List<String> admins = recipientService.resolveRecipientsForOrganization(plan.organizationId());
            brevoEmailService.sendClubNotificationBroadcastSummaryEmail(summary, users, admins, plan.organizationEmail());
        } catch (Exception ex) {
            log.error("Club Notification broadcast summary failed. broadcastId: {}, reason: {}", broadcastId, ex.getMessage(), ex);
        }
    }

    private void putPlan(
            String key,
            int originalCandidates,
            int healthExcludedCount,
            int eligibleCount,
            int batchCount,
            String message,
            String recipientTypeLabel,
            Long organizationId,
            Long branchId,
            String branchLabel,
            String organizationName,
            String organizationPhone,
            String organizationEmail,
            LocalDateTime deadline,
            long expiryEpoch,
            String lifecycle) {
        Map<String, String> fields = new HashMap<>();
        fields.put("originalCandidates", String.valueOf(originalCandidates));
        fields.put("healthExcludedCount", String.valueOf(healthExcludedCount));
        fields.put("eligibleCount", String.valueOf(eligibleCount));
        fields.put("batchCount", String.valueOf(batchCount));
        fields.put("message", safe(message));
        fields.put("recipientTypeLabel", safe(recipientTypeLabel));
        fields.put("organizationId", String.valueOf(organizationId));
        fields.put("branchId", branchId == null ? "" : String.valueOf(branchId));
        fields.put("branchLabel", safe(branchLabel));
        fields.put("organizationName", safe(organizationName));
        fields.put("organizationPhone", safe(organizationPhone));
        fields.put("organizationEmail", safe(organizationEmail));
        fields.put("deadlineAt", deadline.toString());
        fields.put("expiryEpoch", String.valueOf(expiryEpoch));
        fields.put("lifecycle", lifecycle);
        fields.put("summarySent", "false");
        redisTemplate.opsForHash().putAll(key, fields);
        redisTemplate.expire(key, Math.max(60L, (expiryEpoch - System.currentTimeMillis()) / 1000L), TimeUnit.SECONDS);
    }

    private void createBatch(String broadcastId, int batchNumber, List<User> recipients, LocalDateTime scheduledAt, long ttlSeconds) {
        String stateKey = batchStateKey(broadcastId, batchNumber);
        redisTemplate.opsForHash().put(stateKey, "status", PENDING);
        redisTemplate.opsForHash().put(stateKey, "scheduledAt", scheduledAt.toString());
        redisTemplate.opsForHash().put(stateKey, "leaseUntilEpoch", "0");
        redisTemplate.opsForHash().put(stateKey, "recipientCount", String.valueOf(recipients.size()));
        for (int index = 0; index < recipients.size(); index++) {
            User user = recipients.get(index);
            writeRecipient(broadcastId, batchNumber, String.valueOf(index),
                    new BatchRecipient(user.getId(), user.getName(), user.getPhone(), PENDING, null));
        }
        redisTemplate.expire(stateKey, ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.expire(batchRecipientsKey(broadcastId, batchNumber), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForZSet().add(BATCH_DEADLINES_KEY, broadcastId + ":" + batchNumber, toEpochMillis(scheduledAt));
    }

    private void requeueBatch(String broadcastId, int batchNumber) {
        String stateKey = batchStateKey(broadcastId, batchNumber);
        redisTemplate.opsForHash().put(stateKey, "status", PENDING);
        redisTemplate.opsForHash().put(stateKey, "leaseUntilEpoch", "0");
        redisTemplate.opsForZSet().add(
                BATCH_DEADLINES_KEY,
                broadcastId + ":" + batchNumber,
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30));
    }

    private void refreshPlanRetention(String broadcastId, Plan plan, LocalDateTime expiryAt) {
        long ttlSeconds = Math.max(60L, Duration.between(
                Instant.now(), expiryAt.atZone(BUSINESS_ZONE).toInstant()).getSeconds());
        redisTemplate.opsForHash().put(planKey(broadcastId), "expiryEpoch", String.valueOf(toEpochMillis(expiryAt)));
        redisTemplate.expire(planKey(broadcastId), ttlSeconds, TimeUnit.SECONDS);
        for (int batch = 0; batch < plan.batchCount(); batch++) {
            redisTemplate.expire(batchStateKey(broadcastId, batch), ttlSeconds, TimeUnit.SECONDS);
            redisTemplate.expire(batchRecipientsKey(broadcastId, batch), ttlSeconds, TimeUnit.SECONDS);
        }
    }

    private Plan loadPlan(String broadcastId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(planKey(broadcastId));
        if (fields == null || fields.isEmpty()) return null;
        return new Plan(
                parseLong(fields.get("organizationId")),
                parseLongOrNull(fields.get("branchId")),
                safe(fields.get("branchLabel")), safe(fields.get("organizationName")),
                safe(fields.get("organizationPhone")), safe(fields.get("organizationEmail")),
                safe(fields.get("message")), safe(fields.get("recipientTypeLabel")),
                parseInt(fields.get("batchCount")), parseInt(fields.get("originalCandidates")),
                parseInt(fields.get("healthExcludedCount")), parseInt(fields.get("eligibleCount")),
                safe(fields.get("lifecycle")));
    }

    private void writeRecipient(String broadcastId, int batchNumber, String index, BatchRecipient recipient) {
        try {
            redisTemplate.opsForHash().put(batchRecipientsKey(broadcastId, batchNumber), index, objectMapper.writeValueAsString(recipient));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to persist Club Notification recipient state", ex);
        }
    }

    private BatchRecipient readRecipient(String json) {
        try { return objectMapper.readValue(json, BatchRecipient.class); }
        catch (Exception ex) { return null; }
    }

    private String planKey(String broadcastId) { return PREFIX + broadcastId + ":plan"; }
    private String batchStateKey(String broadcastId, int batchNumber) { return PREFIX + broadcastId + ":batch:" + batchNumber + ":state"; }
    private String batchRecipientsKey(String broadcastId, int batchNumber) { return PREFIX + broadcastId + ":batch:" + batchNumber + ":recipients"; }
    private long toEpochMillis(LocalDateTime value) { return value.atZone(BUSINESS_ZONE).toInstant().toEpochMilli(); }
    private int parseInt(Object value) { try { return Integer.parseInt(String.valueOf(value)); } catch (Exception ex) { return 0; } }
    private Long parseLong(Object value) { try { return Long.valueOf(String.valueOf(value)); } catch (Exception ex) { return null; } }
    private Long parseLongOrNull(Object value) { return value == null || String.valueOf(value).isBlank() ? null : parseLong(value); }
    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }

    private record BatchRecipient(Integer userId, String name, String phone, String state, String wamid) {
        private BatchRecipient withState(String newState, String newWamid) {
            return new BatchRecipient(userId, name, phone, newState, newWamid);
        }
    }

    protected record Plan(
            Long organizationId,
            Long branchId,
            String branchLabel,
            String organizationName,
            String organizationPhone,
            String organizationEmail,
            String message,
            String recipientTypeLabel,
            int batchCount,
            int originalCandidates,
            int healthExcludedCount,
            int eligibleCount,
            String lifecycle) {
    }
}
