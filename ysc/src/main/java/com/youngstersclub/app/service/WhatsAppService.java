package com.youngstersclub.app.service;

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
    private static final String PAYMENT_TEMPLATE_NAME = "payment_settled_successfully";
    private static final String PAYMENT_TEMPLATE_LANGUAGE_CODE = "en";
    private static final String DAILY_VISIT_TEMPLATE_NAME = "daily_visit_thank_you";
    private static final String DAILY_VISIT_TEMPLATE_LANGUAGE_CODE = "en";

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${whatsapp.access-token:}")
    private String accessToken;

    @Value("${whatsapp.phone-number-id:}")
    private String phoneNumberId;

    public void sendPaymentSettlementMessage(
            User user,
            BigDecimal paidAmount,
            BigDecimal discountAmount,
            BigDecimal remainingDue) {
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

        try {
            sendTemplateMessage(
                    "payment settlement",
                    phoneNumber,
                    PAYMENT_TEMPLATE_NAME,
                    PAYMENT_TEMPLATE_LANGUAGE_CODE,
                    List.of(
                            Map.of("type", "text", "text", safeText(user.getName())),
                            Map.of("type", "text", "text", formatAmount(paidAmount)),
                            Map.of("type", "text", "text", formatAmount(discountAmount)),
                            Map.of("type", "text", "text", formatAmount(remainingDue))),
                    user.getId());
        } catch (Exception ex) {
            log.warn("WhatsApp message failed for userId: {}. Reason: {}", user.getId(), ex.getMessage());
        }
    }

    public boolean sendDailyVisitThankYouMessage(String phoneNumber, String name) {
        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.warn("Daily WhatsApp thank-you skipped because configuration is missing");
            return false;
        }

        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);
        if (normalizedPhoneNumber == null) {
            return false;
        }

        return sendTemplateMessage(
                "daily visit thank-you",
                normalizedPhoneNumber,
                DAILY_VISIT_TEMPLATE_NAME,
                DAILY_VISIT_TEMPLATE_LANGUAGE_CODE,
                List.of(Map.of("type", "text", "text", safeText(name))),
                null);
    }

    private boolean sendTemplateMessage(
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
                log.info("WhatsApp {} sent successfully for userId: {}", messageType, userId);
                return true;
            }

            log.warn(
                    "WhatsApp {} failed for userId: {} with status: {} and body: {}",
                    messageType,
                    userId,
                    response.getStatusCode(),
                    response.getBody());
            return false;
        } catch (RestClientResponseException ex) {
            log.warn(
                    "WhatsApp {} failed for userId: {}. status: {}, responseBody: {}, reason: {}",
                    messageType,
                    userId,
                    ex.getStatusCode(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("WhatsApp {} failed for userId: {}. Reason: {}", messageType, userId, ex.getMessage());
            return false;
        }
    }

    private String normalizePhoneNumber(String phone) {
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

    private String formatAmount(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return safeAmount.stripTrailingZeros().toPlainString();
    }

    private String safeText(String value) {
        return (value == null || value.isBlank()) ? "Customer" : value.trim();
    }
}
