package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.util.TimeUtil;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
public class BrevoEmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailService.class);
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";
    private static final String SUMMARY_SUBJECT = "Daily WhatsApp Notification Summary";
    private static final String BROADCAST_SUBJECT = "WhatsApp Notification Broadcast Summary";
    private static final String PAYMENT_DUE_REMINDER_SUBJECT = "Payment Due Reminder Summary";
    private static final String HAPPY_BIRTHDAY_WISHES_SUBJECT = "Happy Birthday Wishes Summary";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final class CustomerSummary {
        private final String name;
        private final String phone;

        private CustomerSummary(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    private static final class UserSummary {
        private final String name;
        private final String phone;

        private UserSummary(String name, String phone) {
            this.name = name;
            this.phone = phone;
        }
    }

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.sender-email:}")
    private String senderEmail;

    public int sendSummaryEmail(
            List<UserRepository.DailyVisitedCustomerProjection> customers,
            List<String> adminEmails,
            boolean isDryRun) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Brevo summary email skipped because no admin recipient emails were found");
            return 0;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.warn("Brevo summary email skipped because configuration is missing");
            return 0;
        }

        LocalDate currentDate = TimeUtil.nowIST().toLocalDate();
        List<CustomerSummary> sortedCustomers = (customers == null ? List.<UserRepository.DailyVisitedCustomerProjection>of() : customers)
                .stream()
                .map(customer -> new CustomerSummary(
                        sanitizeName(customer.getName()),
                        sanitizePhone(customer.getPhone())))
                .collect(Collectors.toMap(
                        customer -> customer.name.toLowerCase() + "|" + customer.phone,
                        customer -> customer,
                        (existing, ignored) -> existing))
                .values()
                .stream()
                .sorted((left, right) -> left.name.compareToIgnoreCase(right.name))
                .toList();

        String htmlContent = buildSummaryHtml(currentDate, sortedCustomers, isDryRun);
        int sentCount = 0;

        for (String adminEmail : sanitizeEmails(adminEmails)) {
            try {
                sendToRecipient(adminEmail, htmlContent);
                sentCount++;
                log.info("Brevo summary email sent successfully to {}", adminEmail);
            } catch (RestClientResponseException ex) {
                log.error(
                        "Brevo summary email failed for {}. status: {}, responseBody: {}, reason: {}",
                        adminEmail,
                        ex.getStatusCode(),
                        ex.getResponseBodyAsString(),
                        ex.getMessage());
            } catch (Exception ex) {
                log.error("Brevo summary email failed for {}. Reason: {}", adminEmail, ex.getMessage(), ex);
            }
        }

        return sentCount;
    }

    public int sendNotificationBroadcastSummaryEmail(
            List<User> recipients,
            List<String> adminEmails,
            String recipientTypeLabel,
            String message,
            int successfulSends,
            int failedSends) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Brevo broadcast summary email skipped because no admin recipient emails were found");
            return 0;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.warn("Brevo broadcast summary email skipped because configuration is missing");
            return 0;
        }

        List<UserSummary> sortedRecipients = (recipients == null ? List.<User>of() : recipients)
                .stream()
                .map(user -> new UserSummary(
                        sanitizeName(user.getName()),
                        sanitizePhone(user.getPhone())))
                .collect(Collectors.toMap(
                        recipient -> recipient.name.toLowerCase() + "|" + recipient.phone,
                        recipient -> recipient,
                        (existing, ignored) -> existing))
                .values()
                .stream()
                .sorted((left, right) -> left.name.compareToIgnoreCase(right.name))
                .toList();

        String htmlContent = buildBroadcastSummaryHtml(
                TimeUtil.nowIST().toLocalDate(),
                sortedRecipients,
                recipientTypeLabel,
                message,
                successfulSends,
                failedSends);

        int sentCount = 0;
        for (String adminEmail : sanitizeEmails(adminEmails)) {
            try {
                sendToRecipient(adminEmail, BROADCAST_SUBJECT, htmlContent);
                sentCount++;
                log.info("Brevo broadcast summary email sent successfully to {}", adminEmail);
            } catch (RestClientResponseException ex) {
                log.error(
                        "Brevo broadcast summary email failed for {}. status: {}, responseBody: {}, reason: {}",
                        adminEmail,
                        ex.getStatusCode(),
                        ex.getResponseBodyAsString(),
                        ex.getMessage());
            } catch (Exception ex) {
                log.error("Brevo broadcast summary email failed for {}. Reason: {}", adminEmail, ex.getMessage(), ex);
            }
        }

        return sentCount;
    }

    public int sendPaymentDueReminderSummaryEmail(
            WhatsappTemplateExecutionResultDto result,
            List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Brevo payment due reminder summary email skipped because no admin recipient emails were found");
            return 0;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.warn("Brevo payment due reminder summary email skipped because configuration is missing");
            return 0;
        }

        String htmlContent = buildPaymentDueReminderSummaryHtml(result);
        int sentCount = 0;
        for (String adminEmail : sanitizeEmails(adminEmails)) {
            try {
                sendToRecipient(adminEmail, PAYMENT_DUE_REMINDER_SUBJECT, htmlContent);
                sentCount++;
                log.info("Brevo payment due reminder summary email sent successfully to {}", adminEmail);
            } catch (RestClientResponseException ex) {
                log.error(
                        "Brevo payment due reminder summary email failed for {}. status: {}, responseBody: {}, reason: {}",
                        adminEmail,
                        ex.getStatusCode(),
                        ex.getResponseBodyAsString(),
                        ex.getMessage());
            } catch (Exception ex) {
                log.error("Brevo payment due reminder summary email failed for {}. Reason: {}", adminEmail, ex.getMessage(), ex);
            }
        }

        return sentCount;
    }

    public int sendHappyBirthdayWishesSummaryEmail(
            WhatsappTemplateExecutionResultDto result,
            List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            log.warn("Brevo happy birthday wishes summary email skipped because no admin recipient emails were found");
            return 0;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank() || senderEmail == null || senderEmail.isBlank()) {
            log.warn("Brevo happy birthday wishes summary email skipped because configuration is missing");
            return 0;
        }

        String htmlContent = buildHappyBirthdayWishesSummaryHtml(result);
        int sentCount = 0;
        for (String adminEmail : sanitizeEmails(adminEmails)) {
            try {
                sendToRecipient(adminEmail, HAPPY_BIRTHDAY_WISHES_SUBJECT, htmlContent);
                sentCount++;
                log.info("Brevo happy birthday wishes summary email sent successfully to {}", adminEmail);
            } catch (RestClientResponseException ex) {
                log.error(
                        "Brevo happy birthday wishes summary email failed for {}. status: {}, responseBody: {}, reason: {}",
                        adminEmail,
                        ex.getStatusCode(),
                        ex.getResponseBodyAsString(),
                        ex.getMessage());
            } catch (Exception ex) {
                log.error("Brevo happy birthday wishes summary email failed for {}. Reason: {}", adminEmail, ex.getMessage(), ex);
            }
        }

        return sentCount;
    }

    private void sendToRecipient(String adminEmail, String htmlContent) {
        sendToRecipient(adminEmail, SUMMARY_SUBJECT, htmlContent);
    }

    private void sendToRecipient(String adminEmail, String subject, String htmlContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", brevoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "sender", Map.of(
                        "name", "Youngsters Sports Club",
                        "email", senderEmail),
                "to", List.of(Map.of("email", adminEmail)),
                "subject", subject,
                "htmlContent", htmlContent);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, entity, String.class);
        log.info("Brevo summary email response for {}. status: {}, body: {}", adminEmail, response.getStatusCode(), response.getBody());
    }

    private String buildSummaryHtml(LocalDate currentDate, List<CustomerSummary> customerSummaries, boolean isDryRun) {
        String customerListHtml = customerSummaries.isEmpty()
                ? "<li>No users found.</li>"
                : customerSummaries.stream()
                        .map(summary -> "<li>" + escapeHtml(summary.name) + " - " + escapeHtml(summary.phone) + "</li>")
                        .collect(Collectors.joining());

        return "<h3>Daily WhatsApp Notification Summary</h3>"
                + "<p>Mode: " + (isDryRun ? "DRY RUN" : "ACTUAL RUN") + "</p>"
                + "<p>Date: " + escapeHtml(currentDate.format(DATE_FORMATTER)) + "</p>"
                + "<p>Total Users Notified: " + customerSummaries.size() + "</p>"
                + "<ul>" + customerListHtml + "</ul>";
    }

    private String buildBroadcastSummaryHtml(
            LocalDate currentDate,
            List<UserSummary> recipients,
            String recipientTypeLabel,
            String message,
            int successfulSends,
            int failedSends) {
        String recipientListHtml = recipients.isEmpty()
                ? "<li>No users found.</li>"
                : recipients.stream()
                        .map(summary -> "<li>" + escapeHtml(summary.name) + " - " + escapeHtml(summary.phone) + "</li>")
                        .collect(Collectors.joining());

        return "<h3>WhatsApp Notification Broadcast Summary</h3>"
                + "<p>WhatsApp notification has been sent successfully.</p>"
                + "<p>Date: " + escapeHtml(currentDate.format(DATE_FORMATTER)) + "</p>"
                + "<p>Recipient Type: " + escapeHtml(recipientTypeLabel) + "</p>"
                + "<p>Recipients Count: " + recipients.size() + "</p>"
                + "<p>Successful Sends: " + successfulSends + "</p>"
                + "<p>Failed Sends: " + failedSends + "</p>"
                + "<p>Message:</p>"
                + "<div style=\"padding:12px;border-radius:8px;background:#f8fafc;border:1px solid #dbe4ee;white-space:pre-wrap;\">"
                + escapeHtml(message)
                + "</div>"
                + "<p style=\"margin-top:16px;\">Recipients:</p>"
                + "<ul>" + recipientListHtml + "</ul>";
    }

    private String buildPaymentDueReminderSummaryHtml(WhatsappTemplateExecutionResultDto result) {
        List<WhatsappTemplateExecutionRecipientDto> recipients = result == null ? List.of() : result.getRecipients();
        String recipientListHtml = recipients.isEmpty()
                ? "<li>No eligible customers found.</li>"
                : recipients.stream()
                        .map(recipient -> "<li>"
                                + escapeHtml(sanitizeName(recipient.getName()))
                                + "<br/>Due : ₹"
                                + escapeHtml(recipient.getAmount() == null ? "0" : recipient.getAmount().stripTrailingZeros().toPlainString())
                                + "</li>")
                        .collect(Collectors.joining());

        LocalDateTime executionTime = result == null ? TimeUtil.nowIST() : result.getExecutionTime();

        return "<h3>Payment Due Reminder Summary</h3>"
                + "<p>Execution Time: " + escapeHtml(executionTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a"))) + "</p>"
                + "<p>Mode: " + ((result != null && result.isDryRun()) ? "DRY RUN" : "ACTUAL RUN") + "</p>"
                + "<p>Total Customers Scanned: " + (result == null ? 0 : result.getTotalCustomersScanned()) + "</p>"
                + "<p>Eligible Customers: " + (result == null ? 0 : result.getEligibleCustomers()) + "</p>"
                + "<p>Messages Sent Successfully: " + (result == null ? 0 : result.getSuccessfulMessages()) + "</p>"
                + "<p>Failed Messages: " + (result == null ? 0 : result.getFailedMessages()) + "</p>"
                + "<hr/>"
                + "<ul>" + recipientListHtml + "</ul>";
    }

    private String buildHappyBirthdayWishesSummaryHtml(WhatsappTemplateExecutionResultDto result) {
        List<WhatsappTemplateExecutionRecipientDto> recipients = result == null ? List.of() : result.getRecipients();
        String recipientListHtml = recipients.isEmpty()
                ? "<li>No birthdays found for today.</li>"
                : recipients.stream()
                        .map(recipient -> "<li>"
                                + "Customer : " + escapeHtml(sanitizeName(recipient.getName()))
                                + "<br/>Kid : " + escapeHtml(sanitizeName(recipient.getDetail()))
                                + "<br/>Phone : " + escapeHtml(sanitizePhone(recipient.getPhone()))
                                + "</li>")
                        .collect(Collectors.joining());

        LocalDateTime executionTime = result == null ? TimeUtil.nowIST() : result.getExecutionTime();

        return "<h3>Happy Birthday Wishes Summary</h3>"
                + "<p>Execution Time: " + escapeHtml(executionTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a"))) + "</p>"
                + "<p>Mode: " + ((result != null && result.isDryRun()) ? "DRY RUN" : "ACTUAL RUN") + "</p>"
                + "<p>Today's Birthdays : " + (result == null ? 0 : result.getTotalCustomersScanned()) + "</p>"
                + "<p>Messages Sent : " + (result == null ? 0 : result.getSuccessfulMessages()) + "</p>"
                + "<p>Failed : " + (result == null ? 0 : result.getFailedMessages()) + "</p>"
                + "<hr/>"
                + "<ul>" + recipientListHtml + "</ul>";
    }

    private List<String> sanitizeEmails(List<String> emails) {
        Set<String> uniqueEmails = new LinkedHashSet<>();
        for (String email : emails) {
            if (email != null) {
                String normalized = email.trim().toLowerCase();
                if (!normalized.isBlank()) {
                    uniqueEmails.add(normalized);
                }
            }
        }
        return List.copyOf(uniqueEmails);
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String sanitizeName(String value) {
        return (value == null || value.isBlank()) ? "Customer" : value.trim();
    }

    private String sanitizePhone(String value) {
        return (value == null || value.isBlank()) ? "Phone not available" : value.trim();
    }
}
