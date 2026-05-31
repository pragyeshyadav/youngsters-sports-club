package com.youngstersclub.app.service;

import com.youngstersclub.app.repository.UserRepository;
import com.youngstersclub.app.util.TimeUtil;
import java.time.LocalDate;
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
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private static final class CustomerSummary {
        private final String name;
        private final String phone;

        private CustomerSummary(String name, String phone) {
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

    private void sendToRecipient(String adminEmail, String htmlContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("api-key", brevoApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> requestBody = Map.of(
                "sender", Map.of(
                        "name", "Youngsters Sports Club",
                        "email", senderEmail),
                "to", List.of(Map.of("email", adminEmail)),
                "subject", SUMMARY_SUBJECT,
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
