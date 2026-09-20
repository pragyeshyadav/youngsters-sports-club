package com.youngstersclub.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.youngstersclub.app.dto.WhatsAppTrackedMessageDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Stores and reads the temporary health of a WhatsApp destination. Health is
 * deliberately scoped to an organization and phone number, not a user ID.
 */
@Service
public class WhatsAppRecipientHealthService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppRecipientHealthService.class);
    private static final String PREFIX = "ysc:whatsapp:recipient-status:v1:";
    private static final String CLUB_NOTIFICATION_TEMPLATE = "club_customer_notification_org_wise";
    private static final String EXCLUDE_UNTIL_FIELD = "excludeUntilEpoch";
    private static final String INDEX_PREFIX = "ysc:whatsapp:recipient-status-index:v1:";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DefaultRedisScript<Long> UPDATE_SCRIPT = new DefaultRedisScript<>(
            "local oldEpoch = redis.call('HGET', KEYS[1], 'lastWebhookAtEpoch') "
                    + "local oldRank = redis.call('HGET', KEYS[1], 'lastStatusRank') "
                    + "if oldEpoch and tonumber(oldEpoch) > tonumber(ARGV[1]) then return 0 end "
                    + "if oldEpoch and tonumber(oldEpoch) == tonumber(ARGV[1]) and oldRank "
                    + "and tonumber(oldRank) > tonumber(ARGV[2]) then return 0 end "
                    + "redis.call('HSET', KEYS[1], 'lastStatus', ARGV[3], 'lastStatusRank', ARGV[2], "
                    + "'lastWebhookAtEpoch', ARGV[1], 'lastWebhookAt', ARGV[4], 'lastWamid', ARGV[5], "
                    + "'customerName', ARGV[10], 'customerPhone', ARGV[11]) "
                    + "for index = 2, 5 do redis.call('ZREM', KEYS[index], ARGV[12]) end "
                    + "local statusIndex = 0 "
                    + "if ARGV[3] == 'READ' then statusIndex = 2 "
                    + "elseif ARGV[3] == 'DELIVERED' then statusIndex = 3 "
                    + "elseif ARGV[3] == 'SENT' then statusIndex = 4 "
                    + "elseif ARGV[3] == 'FAILED' then statusIndex = 5 end "
                    + "if statusIndex > 0 then redis.call('ZADD', KEYS[statusIndex], ARGV[1], ARGV[12]) end "
                    + "if ARGV[3] == 'DELIVERED' or ARGV[3] == 'READ' then "
                    + "redis.call('HDEL', KEYS[1], 'lastErrorCode', 'lastErrorTitle', 'lastErrorMessage', 'lastErrorDetails', 'excludeUntilEpoch', 'excludeUntil') "
                    + "elseif ARGV[3] == 'FAILED' then "
                    + "if ARGV[6] == '' then redis.call('HDEL', KEYS[1], 'lastErrorCode') else redis.call('HSET', KEYS[1], 'lastErrorCode', ARGV[6]) end "
                    + "if ARGV[7] ~= '' then redis.call('HSET', KEYS[1], 'excludeUntilEpoch', ARGV[7], 'excludeUntil', ARGV[8]) end "
                    + "end "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[9]) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;
    private final long exclusion131049Seconds;
    private final long exclusion131026Seconds;
    private final long exclusion130472Seconds;

    public WhatsAppRecipientHealthService(
            StringRedisTemplate redisTemplate,
            @Value("${whatsapp.recipient-status.ttl-days:15}") long ttlDays,
            @Value("${whatsapp.recipient-status.131049-exclusion-hours:48}") long exclusion131049Hours,
            @Value("${whatsapp.recipient-status.131026-exclusion-days:15}") long exclusion131026Days,
            @Value("${whatsapp.recipient-status.130472-exclusion-days:15}") long exclusion130472Days) {
        if (ttlDays <= 0 || exclusion131049Hours <= 0 || exclusion131026Days <= 0 || exclusion130472Days <= 0) {
            throw new IllegalArgumentException("WhatsApp recipient-status durations must be positive");
        }
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlDays * 24L * 60L * 60L;
        this.exclusion131049Seconds = exclusion131049Hours * 60L * 60L;
        this.exclusion131026Seconds = exclusion131026Days * 24L * 60L * 60L;
        this.exclusion130472Seconds = exclusion130472Days * 24L * 60L * 60L;
    }

    public <T> List<T> filterEligible(
            Long organizationId,
            Collection<T> candidates,
            Function<T, String> phoneExtractor) {
        List<T> safeCandidates = candidates == null ? List.of() : List.copyOf(candidates);
        if (safeCandidates.isEmpty() || organizationId == null || redisTemplate == null) {
            return safeCandidates;
        }

        Set<String> normalizedPhones = new LinkedHashSet<>();
        for (T candidate : safeCandidates) {
            if (candidate != null) {
                String normalized = WhatsAppPhoneNumberNormalizer.normalize(phoneExtractor.apply(candidate));
                if (normalized != null) {
                    normalizedPhones.add(normalized);
                }
            }
        }
        Set<String> eligiblePhones = findEligiblePhones(organizationId, normalizedPhones);
        return safeCandidates.stream()
                .filter(candidate -> candidate == null
                        || WhatsAppPhoneNumberNormalizer.normalize(phoneExtractor.apply(candidate)) == null
                        || eligiblePhones.contains(WhatsAppPhoneNumberNormalizer.normalize(phoneExtractor.apply(candidate))))
                .toList();
    }

    public Set<String> findEligiblePhones(Long organizationId, Collection<String> normalizedPhones) {
        Set<String> safePhones = normalizedPhones == null ? Set.of() : new LinkedHashSet<>(normalizedPhones);
        if (safePhones.isEmpty() || organizationId == null || redisTemplate == null) {
            return safePhones;
        }
        List<String> keys = safePhones.stream().map(phone -> key(organizationId, phone)).toList();
        try {
            List<Object> values = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (String healthKey : keys) {
                    connection.hashCommands().hGet(
                            redisTemplate.getStringSerializer().serialize(healthKey),
                            redisTemplate.getStringSerializer().serialize(EXCLUDE_UNTIL_FIELD));
                }
                return null;
            });
            long now = Instant.now().getEpochSecond();
            Set<String> eligible = new LinkedHashSet<>();
            int index = 0;
            for (String phone : safePhones) {
                Object value = values == null || index >= values.size() ? null : values.get(index++);
                if (value == null || parseLong(value) <= now) {
                    eligible.add(phone);
                }
            }
            return eligible;
        } catch (Exception ex) {
            log.warn("WhatsApp recipient health lookup failed. organizationId: {}, recipientCount: {}, reason: {}",
                    organizationId, safePhones.size(), ex.getMessage());
            return safePhones;
        }
    }

    public void recordClubNotificationStatus(
            WhatsAppTrackedMessageDto record,
            JsonNode statusNode) {
        if (record == null
                || record.getOrganizationId() == null
                || record.getCustomerPhone() == null
                || !CLUB_NOTIFICATION_TEMPLATE.equals(record.getTemplateName())
                || statusNode == null
                || redisTemplate == null) {
            return;
        }

        String status = normalize(statusNode.path("status").asText(null));
        if (status == null) {
            return;
        }
        status = status.toUpperCase();
        int rank = statusRank(status);
        if (rank == 0) {
            return;
        }

        long eventEpoch = parseWebhookEpoch(statusNode);
        String eventTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(eventEpoch), BUSINESS_ZONE).toString();
        JsonNode error = statusNode.path("errors").path(0);
        Integer code = error.path("code").isIntegralNumber() ? error.path("code").asInt() : null;
        long exclusionSeconds = exclusionSeconds(code);
        String excludeUntilEpoch = status.equals("FAILED") && exclusionSeconds > 0
                ? String.valueOf(eventEpoch + exclusionSeconds)
                : "";
        String excludeUntil = excludeUntilEpoch.isBlank()
                ? ""
                : LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(excludeUntilEpoch)), BUSINESS_ZONE).toString();
        String normalizedPhone = WhatsAppPhoneNumberNormalizer.normalize(record.getCustomerPhone());
        if (normalizedPhone == null) {
            return;
        }
        try {
            redisTemplate.execute(
                    UPDATE_SCRIPT,
                    List.of(
                            key(record.getOrganizationId(), normalizedPhone),
                            statusIndexKey(record.getOrganizationId(), "READ"),
                            statusIndexKey(record.getOrganizationId(), "DELIVERED"),
                            statusIndexKey(record.getOrganizationId(), "SENT"),
                            statusIndexKey(record.getOrganizationId(), "FAILED")),
                    String.valueOf(eventEpoch),
                    String.valueOf(rank),
                    status,
                    eventTime,
                    safe(record.getWamid()),
                    code == null ? "" : String.valueOf(code),
                    excludeUntilEpoch,
                    excludeUntil,
                    String.valueOf(ttlSeconds),
                    safe(record.getCustomerName()),
                    safe(record.getCustomerPhone()),
                    normalizedPhone);
        } catch (Exception ex) {
            log.warn("WhatsApp recipient health update failed. organizationId: {}, reason: {}",
                    record.getOrganizationId(), ex.getMessage());
        }
    }

    protected int statusRank(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "SENT" -> 1;
            case "DELIVERED" -> 2;
            case "READ" -> 3;
            case "FAILED" -> 4;
            default -> 0;
        };
    }

    protected long exclusionSeconds(Integer errorCode) {
        if (errorCode == null) return 0L;
        return switch (errorCode) {
            case 131049 -> exclusion131049Seconds;
            case 131026 -> exclusion131026Seconds;
            case 130472 -> exclusion130472Seconds;
            default -> 0L;
        };
    }

    protected String key(Long organizationId, String normalizedPhone) {
        return PREFIX + organizationId + ":" + normalizedPhone;
    }

    protected String statusIndexKey(Long organizationId, String status) {
        return INDEX_PREFIX + organizationId + ":" + status;
    }

    private long parseWebhookEpoch(JsonNode statusNode) {
        try {
            String timestamp = normalize(statusNode.path("timestamp").asText(null));
            return timestamp == null ? Instant.now().getEpochSecond() : Long.parseLong(timestamp);
        } catch (Exception ex) {
            return Instant.now().getEpochSecond();
        }
    }

    private Long parseLong(Object value) {
        try { return value == null ? 0L : Long.parseLong(String.valueOf(value)); }
        catch (Exception ex) { return 0L; }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
