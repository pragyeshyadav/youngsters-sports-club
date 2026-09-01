package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.WhatsAppTrackedMessageDto;
import com.youngstersclub.app.dto.WhatsAppTrackingMetadata;
import com.youngstersclub.app.enums.WhatsAppTrackedMessageStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.DefaultTypedTuple;

@ExtendWith(MockitoExtension.class)
class ValkeyWhatsAppMessageStatusStoreTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ZSetOperations<String, String> zSetOperations;

    private TestValkeyStore store;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        store = new TestValkeyStore(redisTemplate, new ObjectMapper());
    }

    @Test
    void trackAcceptedStoresRecordWamidLookupAndIndex() {
        LocalDateTime sentTime = LocalDateTime.of(2026, 8, 29, 10, 15);

        store.trackAccepted(
                new WhatsAppTrackingMetadata(1L, 2L, "Satna", 10, "Rahul", "919999999999", "daily_visit_thanks_message"),
                "wamid.123",
                sentTime);

        String expectedTrackingId = "accepted:wamid.123";
        verify(valueOperations).set(
                "ysc:whatsapp:tracking:record:" + expectedTrackingId,
                store.persistedJsonByTrackingId.get(expectedTrackingId),
                12L,
                TimeUnit.HOURS);
        verify(valueOperations).set("ysc:whatsapp:tracking:wamid:wamid.123", expectedTrackingId, 12L, TimeUnit.HOURS);
        verify(zSetOperations).add("ysc:whatsapp:tracking:index:org:1:day:2026-08-29", expectedTrackingId, store.toEpochMillis(sentTime));
        verify(redisTemplate).expire("ysc:whatsapp:tracking:index:org:1:day:2026-08-29", 12L, TimeUnit.HOURS);
    }

    @Test
    void getMessagesForOrganizationOnDateIncludesCurrentBranchAndOrgWideRecords() {
        LocalDate date = LocalDate.of(2026, 8, 29);
        String branchTrackingId = "accepted:wamid.branch";
        String orgWideTrackingId = "not_accepted:1:all:10:1:test";
        String otherBranchTrackingId = "accepted:wamid.other";

        store.recordsByTrackingId.put(branchTrackingId, tracked(branchTrackingId, 1L, 2L, "Satna", "Rahul", "ACCEPTED"));
        store.recordsByTrackingId.put(orgWideTrackingId, tracked(orgWideTrackingId, 1L, null, "All Branches", "Aryan", "NOT_ACCEPTED"));
        store.recordsByTrackingId.put(otherBranchTrackingId, tracked(otherBranchTrackingId, 1L, 3L, "Rewa", "Prince", "DELIVERED"));

        when(zSetOperations.reverseRangeWithScores("ysc:whatsapp:tracking:index:org:1:day:2026-08-29", 0, 99))
                .thenReturn(new LinkedHashSet<>(List.of(
                        new DefaultTypedTuple<>(branchTrackingId, 30d),
                        new DefaultTypedTuple<>(orgWideTrackingId, 20d),
                        new DefaultTypedTuple<>(otherBranchTrackingId, 10d))));

        var page = store.getMessagesForOrganizationOnDate(1L, 2L, date, 0, 20);

        assertEquals(2, page.getMessages().size());
        assertEquals(List.of("Rahul", "Aryan"), page.getMessages().stream().map(WhatsAppTrackedMessageDto::getCustomerName).toList());
        assertFalse(page.isHasMore());
    }

    @Test
    void getMessagesForOrganizationOnDateReturnsEmptyPageWhenValkeyIsUnavailable() {
        LocalDate date = LocalDate.of(2026, 8, 30);
        when(zSetOperations.reverseRangeWithScores("ysc:whatsapp:tracking:index:org:1:day:2026-08-30", 0, 99))
                .thenThrow(new RuntimeException("Connection refused"));

        var page = store.getMessagesForOrganizationOnDate(1L, 2L, date, 0, 20);

        assertTrue(page.getMessages().isEmpty());
        assertEquals(0, page.getPage());
        assertEquals(20, page.getPageSize());
        assertFalse(page.isHasMore());
    }

    @Test
    void applyWebhookPayloadUpdatesTrackedStatusByWamid() throws Exception {
        String trackingId = "accepted:wamid.abc";
        WhatsAppTrackedMessageDto tracked = tracked(trackingId, 1L, 2L, "Satna", "Rahul", "ACCEPTED");
        store.recordsByTrackingId.put(trackingId, tracked);
        when(valueOperations.get("ysc:whatsapp:tracking:wamid:wamid.abc")).thenReturn(trackingId);

        String payload = """
                {
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "statuses": [
                              {
                                "id": "wamid.abc",
                                "status": "delivered",
                                "timestamp": "1787990400"
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        store.applyWebhookPayload(new ObjectMapper().readTree(payload));

        assertEquals("DELIVERED", store.recordsByTrackingId.get(trackingId).getStatus());
    }

    @Test
    void protectedHelpersNormalizeStatusesAndBranchScope() {
        assertEquals(WhatsAppTrackedMessageStatus.READ, store.mapWebhookStatus("read"));
        assertEquals(WhatsAppTrackedMessageStatus.FAILED, store.mapWebhookStatus("FAILED"));
        assertTrue(store.buildNotAcceptedTrackingId(
                new WhatsAppTrackingMetadata(1L, 2L, "Satna", 10, "Rahul", "9199", "daily_visit_thanks_message"),
                LocalDateTime.of(2026, 8, 29, 12, 0)).startsWith("not_accepted:1:2:10:"));
        assertTrue(store.matchesBranchScope(tracked("t1", 1L, null, "All Branches", "Rahul", "ACCEPTED"), 2L));
        assertFalse(store.matchesBranchScope(tracked("t2", 1L, 3L, "Rewa", "Prince", "ACCEPTED"), 2L));
    }

    private WhatsAppTrackedMessageDto tracked(
            String trackingId,
            Long organizationId,
            Long branchId,
            String branchName,
            String customerName,
            String status) {
        WhatsAppTrackedMessageDto dto = new WhatsAppTrackedMessageDto();
        dto.setTrackingId(trackingId);
        dto.setOrganizationId(organizationId);
        dto.setBranchId(branchId);
        dto.setBranchName(branchName);
        dto.setCustomerName(customerName);
        dto.setCustomerPhone("919999999999");
        dto.setTemplateName("daily_visit_thanks_message");
        dto.setStatus(status);
        dto.setSentTime(LocalDateTime.of(2026, 8, 29, 10, 0));
        dto.setLastStatusUpdatedTime(LocalDateTime.of(2026, 8, 29, 10, 0));
        return dto;
    }

    private static final class TestValkeyStore extends ValkeyWhatsAppMessageStatusStore {
        private final Map<String, WhatsAppTrackedMessageDto> recordsByTrackingId = new LinkedHashMap<>();
        private final Map<String, String> persistedJsonByTrackingId = new LinkedHashMap<>();
        private final ObjectMapper mapper;

        private TestValkeyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
            super(redisTemplate, objectMapper);
            this.mapper = objectMapper.copy().findAndRegisterModules();
        }

        @Override
        protected void persistRecord(WhatsAppTrackedMessageDto record) {
            recordsByTrackingId.put(record.getTrackingId(), record);
            try {
                persistedJsonByTrackingId.put(record.getTrackingId(), mapper.writeValueAsString(record));
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            super.persistRecord(record);
        }

        @Override
        protected WhatsAppTrackedMessageDto loadRecord(String trackingId) {
            return recordsByTrackingId.get(trackingId);
        }
    }
}
