package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.WhatsAppMessageStatusPageDto;
import com.youngstersclub.app.dto.WhatsAppTrackingMetadata;
import com.youngstersclub.app.entity.User;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WhatsAppServiceTest {

    @Test
    void buildPaymentSettlementParametersUsesApprovedOrganizationAwareOrder() {
        RecordingStore store = new RecordingStore();
        TestWhatsAppService whatsAppService = new TestWhatsAppService(store);
        User user = new User();
        user.setId(10);
        user.setName("Pragyesh");
        user.setPhone("9876543210");

        whatsAppService.sendPaymentSettlementMessage(
                user,
                BigDecimal.valueOf(100),
                BigDecimal.TEN,
                BigDecimal.valueOf(40),
                99L,
                "The Cue Society",
                "9765657902",
                77L,
                "Cue Main");

        assertEquals("payment_settled_successfully_org_wise", whatsAppService.templateName);
        assertEquals("en", whatsAppService.languageCode);
        assertEquals("919876543210", whatsAppService.phoneNumber);
        assertEquals("wamid.123", store.lastTrackedWamid);
        assertEquals(6, whatsAppService.parameters.size());
        assertTextParameter(whatsAppService.parameters.get(0), "Pragyesh");
        assertTextParameter(whatsAppService.parameters.get(1), "The Cue Society");
        assertTextParameter(whatsAppService.parameters.get(2), "100");
        assertTextParameter(whatsAppService.parameters.get(3), "10");
        assertTextParameter(whatsAppService.parameters.get(4), "40");
        assertTextParameter(whatsAppService.parameters.get(5), "9765657902");
    }

    @Test
    void buildPaymentSettlementParametersPreservesZeroDiscountAndRemainingDue() {
        TestWhatsAppService whatsAppService = new TestWhatsAppService(new RecordingStore());
        User user = new User();
        user.setId(11);
        user.setName("Aryan");
        user.setPhone("9999999999");

        whatsAppService.sendPaymentSettlementMessage(
                user,
                BigDecimal.valueOf(250),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                44L,
                "Area 7 Snooker Club",
                "1234567890",
                55L,
                "Arena");

        assertEquals(6, whatsAppService.parameters.size());
        assertTextParameter(whatsAppService.parameters.get(3), "0");
        assertTextParameter(whatsAppService.parameters.get(4), "0");
    }

    @Test
    void sendPaymentSettlementMessageSkipsWhenOrganizationPhoneMissing() {
        TestWhatsAppService whatsAppService = new TestWhatsAppService(new RecordingStore());
        User user = new User();
        user.setId(12);
        user.setName("Rahul");
        user.setPhone("9999999999");

        whatsAppService.sendPaymentSettlementMessage(
                user,
                BigDecimal.valueOf(250),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                1L,
                "Youngsters Sports Club",
                "  ",
                2L,
                "Satna");

        assertNull(whatsAppService.templateName);
        assertTrue(whatsAppService.parameters.isEmpty());
    }

    @Test
    void sendClubCustomerNotificationUsesOrganizationAwareTemplateAndParameterOrder() {
        TestWhatsAppService whatsAppService = new TestWhatsAppService(new RecordingStore());

        boolean sent = whatsAppService.sendClubCustomerNotificationMessage(
                "9876543210",
                "Pragyesh",
                "club will be closed today",
                "9765657902",
                "Youngsters Sports Club",
                11L,
                12L,
                "Satna",
                15);

        assertTrue(sent);
        assertEquals("club_customer_notification_org_wise", whatsAppService.templateName);
        assertEquals("en", whatsAppService.languageCode);
        assertEquals("919876543210", whatsAppService.phoneNumber);
        assertEquals(4, whatsAppService.parameters.size());
        assertTextParameter(whatsAppService.parameters.get(0), "Pragyesh");
        assertTextParameter(whatsAppService.parameters.get(1), "club will be closed today");
        assertTextParameter(whatsAppService.parameters.get(2), "9765657902");
        assertTextParameter(whatsAppService.parameters.get(3), "Youngsters Sports Club");
    }

    @Test
    void sendClubCustomerNotificationFallsBackToDefaultPhoneWhenOrganizationPhoneMissing() {
        TestWhatsAppService whatsAppService = new TestWhatsAppService(new RecordingStore());

        boolean sent = whatsAppService.sendClubCustomerNotificationMessage(
                "9876543210",
                "Pragyesh",
                "club will be closed today",
                "   ",
                "The Cue Society",
                11L,
                null,
                "All Branches",
                16);

        assertTrue(sent);
        assertEquals(4, whatsAppService.parameters.size());
        assertTextParameter(whatsAppService.parameters.get(2), "9765657902");
        assertTextParameter(whatsAppService.parameters.get(3), "The Cue Society");
    }

    @Test
    void extractWamidReturnsFirstMessageIdFromMetaResponse() {
        TestWhatsAppService whatsAppService = new TestWhatsAppService(new RecordingStore());

        String wamid = whatsAppService.extractWamid("""
                {"messages":[{"id":"wamid.HBgNODc2NTY1NzkwMhUCABIYFjNFQ0U5QkQ5RkVC"}]}
                """);

        assertEquals("wamid.HBgNODc2NTY1NzkwMhUCABIYFjNFQ0U5QkQ5RkVC", wamid);
    }

    @Test
    void sendTemplateMessageTracksNotAcceptedWhenMetaDoesNotReturnWamid() {
        RecordingStore store = new RecordingStore();
        TestWhatsAppService whatsAppService = new TestWhatsAppService(store);
        whatsAppService.shouldReturnWamid = false;
        User user = new User();
        user.setId(99);
        user.setName("Rahul");
        user.setPhone("9999999999");

        whatsAppService.sendPaymentSettlementMessage(
                user,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                5L,
                "Cue Society",
                "9999999999",
                6L,
                "Main");

        assertEquals("payment_settled_successfully_org_wise", whatsAppService.templateName);
        assertNull(store.lastTrackedWamid);
        assertEquals("Meta accepted response did not include a wamid", store.lastNotAcceptedMessage);
    }

    private void assertTextParameter(Map<String, Object> parameter, String expected) {
        assertEquals("text", parameter.get("type"));
        assertEquals(expected, parameter.get("text"));
    }

    private static final class TestWhatsAppService extends WhatsAppService {
        private final RecordingStore store;
        private String phoneNumber;
        private String templateName;
        private String languageCode;
        private List<Map<String, Object>> parameters = List.of();
        private boolean shouldReturnWamid = true;

        private TestWhatsAppService(RecordingStore store) {
            super(new ObjectMapper(), store);
            this.store = store;
            ReflectionTestUtils.setField(this, "accessToken", "test-access-token");
            ReflectionTestUtils.setField(this, "phoneNumberId", "test-phone-number-id");
        }

        @Override
        protected TemplateSendResult executeTemplateMessage(
                String messageType,
                String phoneNumber,
                String templateName,
                String languageCode,
                List<Map<String, Object>> parameters,
                Integer userId) {
            this.phoneNumber = phoneNumber;
            this.templateName = templateName;
            this.languageCode = languageCode;
            this.parameters = parameters;
            return shouldReturnWamid
                    ? new TemplateSendResult(true, "wamid.123", null, null)
                    : new TemplateSendResult(true, null, null, "Meta accepted response did not include a wamid");
        }
    }

    private static final class RecordingStore implements WhatsAppMessageStatusStore {
        private String lastTrackedWamid;
        private String lastNotAcceptedMessage;

        @Override
        public void trackAccepted(WhatsAppTrackingMetadata metadata, String wamid, LocalDateTime sentTime) {
            this.lastTrackedWamid = wamid;
        }

        @Override
        public void trackNotAccepted(
                WhatsAppTrackingMetadata metadata,
                LocalDateTime sentTime,
                Integer metaErrorCode,
                String metaErrorMessage) {
            this.lastNotAcceptedMessage = metaErrorMessage;
        }

        @Override
        public void applyWebhookPayload(JsonNode payload) {
        }

        @Override
        public WhatsAppMessageStatusPageDto getMessagesForOrganizationOnDate(
                Long organizationId,
                Long branchId,
                LocalDate date,
                int page,
                int pageSize) {
            return new WhatsAppMessageStatusPageDto(List.of(), page, pageSize, false);
        }
    }
}
