package com.youngstersclub.app.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.dto.WhatsAppTrackingMetadata;
import com.youngstersclub.app.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);
    private static final String WHATSAPP_API_BASE_URL = "https://graph.facebook.com/v17.0/";
    private static final String PAYMENT_TEMPLATE_NAME = "payment_settled_successfully_org_wise";
    private static final String PAYMENT_TEMPLATE_LANGUAGE_CODE = "en";
    private static final String DAILY_VISIT_TEMPLATE_NAME = "daily_visit_thanks_message";
    private static final String DAILY_VISIT_TEMPLATE_LANGUAGE_CODE = "en";
    private static final String PAYMENT_DUE_REMINDER_TEMPLATE_NAME = "payment_due_reminder";
    private static final String PAYMENT_DUE_REMINDER_TEMPLATE_LANGUAGE_CODE = "en";
    private static final String HAPPY_BIRTHDAY_WISHES_OFFER_TEMPLATE_NAME = "happy_birthday_wishes_offer";
    private static final String HAPPY_BIRTHDAY_WISHES_OFFER_LANGUAGE_CODE = "en";
    private static final String CLUB_NOTIFICATION_TEMPLATE_NAME = "club_customer_notification_org_wise";
    private static final String CLUB_NOTIFICATION_TEMPLATE_LANGUAGE_CODE = "en";
    private static final String DEFAULT_CLUB_NOTIFICATION_PHONE = "9765657902";

    private final ObjectMapper objectMapper;
    private final WhatsAppMessageStatusStore whatsAppMessageStatusStore;
    private final RestTemplate restTemplate = new RestTemplate();

    public WhatsAppService(
            ObjectMapper objectMapper,
            WhatsAppMessageStatusStore whatsAppMessageStatusStore) {
        this.objectMapper = objectMapper;
        this.whatsAppMessageStatusStore = whatsAppMessageStatusStore;
    }

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    public void sendPaymentSettlementMessage(
            User user,
            BigDecimal paidAmount,
            BigDecimal discountAmount,
            BigDecimal remainingDue,
            Long organizationId,
            String organizationName,
            String organizationPhone,
            Long branchId,
            String branchName) {
        if (user == null || user.getId() == null) {
            log.warn("WhatsApp message skipped because user details are missing");
            return;
        }

        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("WhatsApp message skipped for userId: {} because configuration is missing", user.getId());
            return;
        }

        String phoneNumber = normalizePhoneNumber(user.getPhone());
        if (phoneNumber == null) {
            log.warn("WhatsApp message skipped for userId: {} because phone number is invalid", user.getId());
            return;
        }

        String safeOrganizationName = normalizeOrganizationText(organizationName);
        String safeOrganizationPhone = normalizeOrganizationText(organizationPhone);
        if (safeOrganizationName == null || safeOrganizationPhone == null) {
            log.warn(
                    "WhatsApp payment settlement message skipped for userId: {} because organization details are incomplete",
                    user.getId());
            return;
        }

        try {
            TemplateSendResult result = executeTemplateMessage(
                    "payment settlement",
                    phoneNumber,
                    PAYMENT_TEMPLATE_NAME,
                    PAYMENT_TEMPLATE_LANGUAGE_CODE,
                    buildPaymentSettlementParameters(user, paidAmount, discountAmount, remainingDue, safeOrganizationName, safeOrganizationPhone),
                    user.getId());
            trackSendResult(
                    result,
                    new WhatsAppTrackingMetadata(
                            organizationId,
                            branchId,
                            branchName,
                            user.getId(),
                            user.getName(),
                            phoneNumber,
                            PAYMENT_TEMPLATE_NAME));
        } catch (Exception ex) {
            log.warn("WhatsApp message failed for userId: {}. Reason: {}", user.getId(), ex.getMessage());
        }
    }

    public boolean sendDailyVisitThankYouMessage(
            String phoneNumber,
            String name,
            Long organizationId,
            String organizationName,
            Long branchId,
            String branchName,
            Integer userId) {
        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("Daily WhatsApp thank-you skipped because configuration is missing");
            return false;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            return false;
        }

        TemplateSendResult result = executeTemplateMessage(
                "daily visit thank-you",
                normalizedPhoneNumber,
                DAILY_VISIT_TEMPLATE_NAME,
                DAILY_VISIT_TEMPLATE_LANGUAGE_CODE,
                List.of(Map.of("type", "text", "text", safeText(name))),
                userId);
        trackSendResult(
                result,
                new WhatsAppTrackingMetadata(
                        organizationId,
                        branchId,
                        branchName,
                        userId,
                        name,
                        normalizedPhoneNumber,
                        DAILY_VISIT_TEMPLATE_NAME));
        return result.success();
    }

    public boolean sendClubCustomerNotificationMessage(
            String phoneNumber,
            String name,
            String message,
            String organizationPhone,
            String organizationName,
            Long organizationId,
            Long branchId,
            String branchName,
            Integer userId) {
        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("Club customer notification skipped for userId: {} because configuration is missing", userId);
            return false;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            log.warn("Club customer notification skipped for userId: {} because phone number is invalid", userId);
            return false;
        }

        String resolvedOrganizationName = normalizeOrganizationText(organizationName);
        if (resolvedOrganizationName == null) {
            log.warn("Club customer notification skipped for userId: {} because organization name is missing", userId);
            return false;
        }

        TemplateSendResult result = executeTemplateMessage(
                "club customer notification",
                normalizedPhoneNumber,
                CLUB_NOTIFICATION_TEMPLATE_NAME,
                CLUB_NOTIFICATION_TEMPLATE_LANGUAGE_CODE,
                buildClubCustomerNotificationParameters(
                        name,
                        message,
                        resolveClubCustomerNotificationPhone(organizationPhone),
                        resolvedOrganizationName),
                userId);
        trackSendResult(
                result,
                new WhatsAppTrackingMetadata(
                        organizationId,
                        branchId,
                        branchName,
                        userId,
                        name,
                        normalizedPhoneNumber,
                        CLUB_NOTIFICATION_TEMPLATE_NAME));
        return result.success();
    }

    public boolean sendPaymentDueReminderMessage(
            String phoneNumber,
            String name,
            BigDecimal totalDue,
            Long organizationId,
            Long branchId,
            String branchName,
            Integer userId) {
        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("Payment due reminder skipped for userId: {} because configuration is missing", userId);
            return false;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            log.warn("Payment due reminder skipped for userId: {} because phone number is invalid", userId);
            return false;
        }

        TemplateSendResult result = executeTemplateMessage(
                "payment due reminder",
                normalizedPhoneNumber,
                PAYMENT_DUE_REMINDER_TEMPLATE_NAME,
                PAYMENT_DUE_REMINDER_TEMPLATE_LANGUAGE_CODE,
                List.of(
                        Map.of("type", "text", "text", safeText(name)),
                        Map.of("type", "text", "text", formatAmount(totalDue))),
                userId);
        trackSendResult(
                result,
                new WhatsAppTrackingMetadata(
                        organizationId,
                        branchId,
                        branchName,
                        userId,
                        name,
                        normalizedPhoneNumber,
                        PAYMENT_DUE_REMINDER_TEMPLATE_NAME));
        return result.success();
    }

    public boolean sendHappyBirthdayWishesOfferMessage(
            String phoneNumber,
            String kidName,
            Long organizationId,
            Long branchId,
            String branchName,
            Integer userId,
            String customerName) {
        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("Happy birthday wishes offer skipped for userId: {} because configuration is missing", userId);
            return false;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            log.warn("Happy birthday wishes offer skipped for userId: {} because phone number is invalid", userId);
            return false;
        }

        TemplateSendResult result = executeTemplateMessage(
                "happy birthday wishes offer",
                normalizedPhoneNumber,
                HAPPY_BIRTHDAY_WISHES_OFFER_TEMPLATE_NAME,
                HAPPY_BIRTHDAY_WISHES_OFFER_LANGUAGE_CODE,
                List.of(Map.of("type", "text", "text", safeText(kidName))),
                userId);
        trackSendResult(
                result,
                new WhatsAppTrackingMetadata(
                        organizationId,
                        branchId,
                        branchName,
                        userId,
                        customerName,
                        normalizedPhoneNumber,
                        HAPPY_BIRTHDAY_WISHES_OFFER_TEMPLATE_NAME));
        return result.success();
    }

    protected List<Map<String, Object>> buildPaymentSettlementParameters(
            User user,
            BigDecimal paidAmount,
            BigDecimal discountAmount,
            BigDecimal remainingDue,
            String organizationName,
            String organizationPhone) {
        return List.of(
                Map.of("type", "text", "text", safeText(user == null ? null : user.getName())),
                Map.of("type", "text", "text", organizationName),
                Map.of("type", "text", "text", formatAmount(paidAmount)),
                Map.of("type", "text", "text", formatAmount(discountAmount)),
                Map.of("type", "text", "text", formatAmount(remainingDue)),
                Map.of("type", "text", "text", organizationPhone));
    }

    protected List<Map<String, Object>> buildClubCustomerNotificationParameters(
            String customerName,
            String message,
            String organizationPhone,
            String organizationName) {
        return List.of(
                Map.of("type", "text", "text", safeText(customerName)),
                Map.of("type", "text", "text", safeText(message)),
                Map.of("type", "text", "text", organizationPhone),
                Map.of("type", "text", "text", organizationName));
    }

    protected boolean sendTemplateMessage(
            String messageType,
            String phoneNumber,
            String templateName,
            String languageCode,
            List<Map<String, Object>> parameters,
            Integer userId) {
        return executeTemplateMessage(messageType, phoneNumber, templateName, languageCode, parameters, userId).success();
    }

    protected TemplateSendResult executeTemplateMessage(
            String messageType,
            String phoneNumber,
            String templateName,
            String languageCode,
            List<Map<String, Object>> parameters,
            Integer userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = Map.of(
                    "messaging_product", "whatsapp",
                    "to", phoneNumber,
                    "type", "template",
                    "template", Map.of(
                            "name", templateName,
                            "language", Map.of("code", languageCode),
                            "components", List.of(
                                    Map.of(
                                            "type", "body",
                                            "parameters", parameters))));

            log.info(
                    "Sending WhatsApp {} template. userId: {}, templateName: {}, languageCode: {}, phoneNumber: {}, payload: {}",
                    messageType,
                    userId,
                    templateName,
                    languageCode,
                    phoneNumber,
                    requestBody);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    WHATSAPP_API_BASE_URL + phoneNumberId + "/messages",
                    entity,
                    String.class);

            log.info(
                    "WhatsApp API response for {}. userId: {}, status: {}, body: {}",
                    messageType,
                    userId,
                    response.getStatusCode(),
                    response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                String wamid = extractWamid(response.getBody());
                log.info("WhatsApp {} sent successfully for userId: {}", messageType, userId);
                return new TemplateSendResult(true, wamid, null, null);
            }

            log.warn(
                    "WhatsApp {} failed for userId: {} with status: {} and body: {}",
                    messageType,
                    userId,
                    response.getStatusCode(),
                    response.getBody());
            return new TemplateSendResult(
                    false,
                    null,
                    response.getStatusCode().value(),
                    response.getBody());
        } catch (RestClientResponseException ex) {
            log.warn(
                    "WhatsApp {} failed for userId: {}. status: {}, responseBody: {}, reason: {}",
                    messageType,
                    userId,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage());
            return new TemplateSendResult(
                    false,
                    null,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("WhatsApp {} failed for userId: {}. Reason: {}", messageType, userId, ex.getMessage());
            return new TemplateSendResult(false, null, null, ex.getMessage());
        }
    }

    protected void trackSendResult(TemplateSendResult result, WhatsAppTrackingMetadata metadata) {
        if (result == null || metadata == null || metadata.organizationId() == null) {
            return;
        }
        if (result.wamid() != null) {
            whatsAppMessageStatusStore.trackAccepted(metadata, result.wamid(), java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")));
            return;
        }
        whatsAppMessageStatusStore.trackNotAccepted(
                metadata,
                java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Kolkata")),
                result.errorCode(),
                result.errorMessage());
    }

    protected String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }

        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits;
        }
        return null;
    }

    protected String formatAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return safeAmount.stripTrailingZeros().toPlainString();
    }

    protected String safeText(String value) {
        return (value == null || value.isBlank()) ? "Customer" : value.trim();
    }

    protected String normalizeOrganizationText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected String extractWamid(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(responseBody);
            JsonNode messagesNode = payload.path("messages");
            if (!messagesNode.isArray() || messagesNode.isEmpty()) {
                return null;
            }
            return normalizeOrganizationText(messagesNode.path(0).path("id").asText(null));
        } catch (Exception ex) {
            log.warn("Unable to parse WhatsApp response wamid. Reason: {}", ex.getMessage());
            return null;
        }
    }

    protected String resolveClubCustomerNotificationPhone(String organizationPhone) {
        String normalizedPhone = normalizeOrganizationText(organizationPhone);
        return normalizedPhone == null ? DEFAULT_CLUB_NOTIFICATION_PHONE : normalizedPhone;
    }

    protected record TemplateSendResult(
            boolean success,
            String wamid,
            Integer errorCode,
            String errorMessage) {
    }
}
