package com.youngstersclub.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.WhatsAppMessageStatusPageDto;
import com.youngstersclub.app.dto.WhatsAppTrackedMessageDto;
import com.youngstersclub.app.dto.WhatsAppTrackingMetadata;
import com.youngstersclub.app.enums.WhatsAppTrackedMessageStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class ValkeyWhatsAppMessageStatusStore implements WhatsAppMessageStatusStore {

    private static final Logger log = LoggerFactory.getLogger(ValkeyWhatsAppMessageStatusStore.class);
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final long TTL_HOURS = 12L;
    private static final int FETCH_BATCH_SIZE = 100;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ValkeyWhatsAppMessageStatusStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Override
    public void trackAccepted(WhatsAppTrackingMetadata metadata, String wamid, LocalDateTime sentTime) {
        if (metadata == null || metadata.organizationId() == null || wamid == null || wamid.isBlank()) {
            return;
        }
        try {
            String trackingId = buildAcceptedTrackingId(wamid);
            WhatsAppTrackedMessageDto record = buildRecord(
                    trackingId,
                    wamid,
                    metadata,
                    WhatsAppTrackedMessageStatus.ACCEPTED,
                    sentTime,
                    null,
                    null);
            persistRecord(record);
            writeWamidLookup(wamid, trackingId);
        } catch (Exception ex) {
            log.warn("Unable to track accepted WhatsApp message. wamid: {}. Reason: {}", wamid, ex.getMessage());
        }
    }

    @Override
    public void trackNotAccepted(
            WhatsAppTrackingMetadata metadata,
            LocalDateTime sentTime,
            Integer metaErrorCode,
            String metaErrorMessage) {
        if (metadata == null || metadata.organizationId() == null) {
            return;
        }
        try {
            String trackingId = buildNotAcceptedTrackingId(metadata, sentTime);
            WhatsAppTrackedMessageDto record = buildRecord(
                    trackingId,
                    null,
                    metadata,
                    WhatsAppTrackedMessageStatus.NOT_ACCEPTED,
                    sentTime,
                    metaErrorCode,
                    metaErrorMessage);
            persistRecord(record);
        } catch (Exception ex) {
            log.warn("Unable to track not accepted WhatsApp message. Reason: {}", ex.getMessage());
        }
    }

    @Override
    public void applyWebhookPayload(JsonNode payload) {
        if (payload == null) {
            return;
        }
        try {
            for (JsonNode entryNode : safeArray(payload.path("entry"))) {
                for (JsonNode changeNode : safeArray(entryNode.path("changes"))) {
                    JsonNode statusesNode = changeNode.path("value").path("statuses");
                    for (JsonNode statusNode : safeArray(statusesNode)) {
                        applySingleStatus(statusNode);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to apply WhatsApp webhook payload. Reason: {}", ex.getMessage());
        }
    }

    @Override
    public WhatsAppMessageStatusPageDto getMessagesForOrganizationOnDate(
            Long organizationId,
            Long branchId,
            LocalDate date,
            int page,
            int pageSize) {
        if (organizationId == null || date == null) {
            return new WhatsAppMessageStatusPageDto(List.of(), Math.max(page, 0), pageSize, false);
        }

        int safePage = Math.max(page, 0);
        int safePageSize = pageSize <= 0 ? 20 : pageSize;
        int startIndex = safePage * safePageSize;
        List<WhatsAppTrackedMessageDto> filteredMessages = new ArrayList<>();
        int matchedCount = 0;
        long offset = 0L;
        boolean moreAvailable = false;

        try {
            String indexKey = buildOrganizationDayIndexKey(organizationId, date);
            while (true) {
                int endIndex = (int) (offset + FETCH_BATCH_SIZE - 1);
                java.util.Set<ZSetOperations.TypedTuple<String>> tuples =
                        redisTemplate.opsForZSet().reverseRangeWithScores(indexKey, offset, endIndex);
                if (tuples == null || tuples.isEmpty()) {
                    break;
                }

                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    String trackingId = tuple == null ? null : tuple.getValue();
                    WhatsAppTrackedMessageDto record = loadRecord(trackingId);
                    if (!matchesBranchScope(record, branchId)) {
                        continue;
                    }

                    if (matchedCount >= startIndex && filteredMessages.size() < safePageSize) {
                        filteredMessages.add(record);
                    } else if (matchedCount >= startIndex + safePageSize) {
                        moreAvailable = true;
                        break;
                    }

                    matchedCount++;
                }

                if (moreAvailable || tuples.size() < FETCH_BATCH_SIZE) {
                    break;
                }
                offset += FETCH_BATCH_SIZE;
            }
        } catch (Exception ex) {
            log.warn(
                    "Unable to load WhatsApp message statuses from Valkey. organizationId: {}, branchId: {}, date: {}, page: {}. Reason: {}",
                    organizationId,
                    branchId,
                    date,
                    safePage,
                    ex.getMessage());
            return new WhatsAppMessageStatusPageDto(List.of(), safePage, safePageSize, false);
        }

        return new WhatsAppMessageStatusPageDto(filteredMessages, safePage, safePageSize, moreAvailable);
    }

    protected String buildAcceptedTrackingId(String wamid) {
        return "accepted:" + wamid.trim();
    }

    protected String buildNotAcceptedTrackingId(WhatsAppTrackingMetadata metadata, LocalDateTime sentTime) {
        long epochMillis = toEpochMillis(sentTime == null ? LocalDateTime.now(IST_ZONE) : sentTime);
        String branchSegment = metadata.branchId() == null ? "all" : String.valueOf(metadata.branchId());
        String userSegment = metadata.userId() == null ? "unknown" : String.valueOf(metadata.userId());
        return "not_accepted:%s:%s:%s:%d:%s".formatted(
                metadata.organizationId(),
                branchSegment,
                userSegment,
                epochMillis,
                UUID.randomUUID());
    }

    protected String buildRecordKey(String trackingId) {
        return "ysc:whatsapp:tracking:record:" + trackingId;
    }

    protected String buildOrganizationDayIndexKey(Long organizationId, LocalDate date) {
        return "ysc:whatsapp:tracking:index:org:%d:day:%s".formatted(
                organizationId,
                date == null ? "unknown" : date.toString());
    }

    protected String buildWamidLookupKey(String wamid) {
        return "ysc:whatsapp:tracking:wamid:" + wamid;
    }

    protected WhatsAppTrackedMessageStatus mapWebhookStatus(String rawStatus) {
        String normalized = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sent" -> WhatsAppTrackedMessageStatus.SENT;
            case "delivered" -> WhatsAppTrackedMessageStatus.DELIVERED;
            case "read" -> WhatsAppTrackedMessageStatus.READ;
            case "failed" -> WhatsAppTrackedMessageStatus.FAILED;
            default -> null;
        };
    }

    protected boolean matchesBranchScope(WhatsAppTrackedMessageDto record, Long branchId) {
        if (record == null) {
            return false;
        }
        if (branchId == null) {
            return true;
        }
        return record.getBranchId() == null || Objects.equals(record.getBranchId(), branchId);
    }

    protected void applySingleStatus(JsonNode statusNode) {
        String wamid = normalizeText(statusNode.path("id").asText(null));
        if (wamid == null) {
            return;
        }

        String trackingId = normalizeText(redisTemplate.opsForValue().get(buildWamidLookupKey(wamid)));
        if (trackingId == null) {
            log.debug("WhatsApp webhook status ignored because tracked wamid was not found. wamid: {}", wamid);
            return;
        }

        WhatsAppTrackedMessageDto record = loadRecord(trackingId);
        if (record == null) {
            return;
        }

        WhatsAppTrackedMessageStatus mappedStatus = mapWebhookStatus(statusNode.path("status").asText(null));
        if (mappedStatus == null) {
            return;
        }

        record.setStatus(mappedStatus.name());
        record.setLastStatusUpdatedTime(resolveWebhookTimestamp(statusNode));
        if (mappedStatus == WhatsAppTrackedMessageStatus.FAILED) {
            JsonNode errorNode = statusNode.path("errors").path(0);
            if (!errorNode.isMissingNode()) {
                record.setMetaErrorCode(errorNode.path("code").isIntegralNumber() ? errorNode.path("code").asInt() : null);
                record.setMetaErrorMessage(normalizeText(errorNode.path("title").asText(null)));
            }
        }
        persistRecord(record);
    }

    protected LocalDateTime resolveWebhookTimestamp(JsonNode statusNode) {
        String rawTimestamp = normalizeText(statusNode.path("timestamp").asText(null));
        if (rawTimestamp == null) {
            return LocalDateTime.now(IST_ZONE);
        }
        try {
            long seconds = Long.parseLong(rawTimestamp);
            return LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(seconds), IST_ZONE);
        } catch (NumberFormatException ex) {
            return LocalDateTime.now(IST_ZONE);
        }
    }

    protected WhatsAppTrackedMessageDto buildRecord(
            String trackingId,
            String wamid,
            WhatsAppTrackingMetadata metadata,
            WhatsAppTrackedMessageStatus status,
            LocalDateTime sentTime,
            Integer metaErrorCode,
            String metaErrorMessage) {
        LocalDateTime safeSentTime = sentTime == null ? LocalDateTime.now(IST_ZONE) : sentTime;
        WhatsAppTrackedMessageDto record = new WhatsAppTrackedMessageDto();
        record.setTrackingId(trackingId);
        record.setWamid(wamid);
        record.setOrganizationId(metadata.organizationId());
        record.setBranchId(metadata.branchId());
        record.setBranchName(normalizeText(metadata.branchName()));
        record.setUserId(metadata.userId());
        record.setCustomerName(normalizeText(metadata.customerName()));
        record.setCustomerPhone(normalizeText(metadata.customerPhone()));
        record.setTemplateName(normalizeText(metadata.templateName()));
        record.setStatus(status.name());
        record.setSentTime(safeSentTime);
        record.setLastStatusUpdatedTime(safeSentTime);
        record.setMetaErrorCode(metaErrorCode);
        record.setMetaErrorMessage(normalizeText(metaErrorMessage));
        return record;
    }

    protected void persistRecord(WhatsAppTrackedMessageDto record) {
        if (record == null || record.getTrackingId() == null || record.getOrganizationId() == null || record.getSentTime() == null) {
            return;
        }
        String json = writeJson(record);
        String recordKey = buildRecordKey(record.getTrackingId());
        redisTemplate.opsForValue().set(recordKey, json, TTL_HOURS, TimeUnit.HOURS);

        String indexKey = buildOrganizationDayIndexKey(record.getOrganizationId(), record.getSentTime().toLocalDate());
        redisTemplate.opsForZSet().add(indexKey, record.getTrackingId(), toEpochMillis(record.getSentTime()));
        redisTemplate.expire(indexKey, TTL_HOURS, TimeUnit.HOURS);
    }

    protected void writeWamidLookup(String wamid, String trackingId) {
        if (wamid == null || trackingId == null) {
            return;
        }
        redisTemplate.opsForValue().set(buildWamidLookupKey(wamid), trackingId, TTL_HOURS, TimeUnit.HOURS);
    }

    protected WhatsAppTrackedMessageDto loadRecord(String trackingId) {
        String normalizedTrackingId = normalizeText(trackingId);
        if (normalizedTrackingId == null) {
            return null;
        }
        String json = redisTemplate.opsForValue().get(buildRecordKey(normalizedTrackingId));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, WhatsAppTrackedMessageDto.class);
        } catch (JsonProcessingException ex) {
            log.warn("Unable to read tracked WhatsApp message record. trackingId: {}. Reason: {}", trackingId, ex.getMessage());
            return null;
        }
    }

    protected String writeJson(WhatsAppTrackedMessageDto record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize WhatsApp tracked message", ex);
        }
    }

    protected List<JsonNode> safeArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(values::add);
        return values;
    }

    protected long toEpochMillis(LocalDateTime dateTime) {
        return dateTime.atZone(IST_ZONE).toInstant().toEpochMilli();
    }

    protected String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
