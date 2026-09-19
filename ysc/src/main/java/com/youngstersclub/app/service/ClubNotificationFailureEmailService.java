package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ClubNotificationFailureReason;
import com.youngstersclub.app.dto.ClubNotificationFailureReport;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ClubNotificationFailureEmailService {

    private static final Logger log = LoggerFactory.getLogger(ClubNotificationFailureEmailService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    private final BrevoEmailService brevoEmailService;
    private final OrganizationSummaryRecipientService recipientService;

    public ClubNotificationFailureEmailService(
            BrevoEmailService brevoEmailService,
            OrganizationSummaryRecipientService recipientService) {
        this.brevoEmailService = brevoEmailService;
        this.recipientService = recipientService;
    }

    @Async
    public void sendAsync(ClubNotificationFailureReport report) {
        try {
            var recipients = recipientService.resolveRecipientsForClubNotificationFailureReport(report.organizationId());
            int sentCount = brevoEmailService.sendClubNotificationFailureReportEmail(report, recipients);
            log.info(
                    "Club notification failure email completed. broadcastId: {}, organizationId: {}, recipientCount: {}",
                    report.broadcastId(),
                    report.organizationId(),
                    sentCount);
        } catch (Exception ex) {
            log.error("Club notification failure email failed. broadcastId: {}, reason: {}", report.broadcastId(), ex.getMessage(), ex);
        }
    }

    protected static String buildSubject(ClubNotificationFailureReport report) {
        return "WhatsApp Notification Failure Report - " + safe(report.organizationName());
    }

    protected static String buildHtml(ClubNotificationFailureReport report) {
        StringBuilder html = new StringBuilder("<h3>WhatsApp Notification Failure Report</h3>");
        html.append(row("Organization", report.organizationName()));
        html.append(row("Template", report.templateName()));
        html.append(row("Broadcast ID", report.broadcastId()));
        html.append(row("Triggered At", format(report.triggeredAt())));
        html.append(row("Report Finalized At", format(report.finalizedAt())));
        html.append(row("Accepted by Meta", String.valueOf(report.acceptedCount())));
        html.append(row("Webhook Failed", String.valueOf(report.failedCount())));
        html.append(row("Non-Failed / Completed", String.valueOf(report.completedCount())));
        html.append(row("Pending at 15-Minute Cutoff", String.valueOf(report.pendingCount())));
        html.append(row("Distinct Failure Reasons", String.valueOf(report.failureReasons().size())));
        if (!report.notificationMessage().isBlank()) {
            html.append("<p><strong>Notification Message:</strong></p><div style=\"padding:12px;border-radius:8px;background:#f8fafc;border:1px solid #dbe4ee;white-space:pre-wrap;\">")
                    .append(escapeHtml(report.notificationMessage()))
                    .append("</div>");
        }
        html.append("<p><strong>Failure Reasons</strong></p><ol>");
        for (ClubNotificationFailureReason reason : report.failureReasons()) {
            html.append("<li>")
                    .append(row("Affected Messages", String.valueOf(reason.affectedCount())))
                    .append(row("Error Code", valueOrUnavailable(reason.code())))
                    .append(row("Title", reason.title()))
                    .append(row("Message", reason.message()))
                    .append(row("Details", reason.details()))
                    .append("</li>");
        }
        html.append("</ol>");
        if (report.pendingCount() > 0) {
            html.append("<p>Pending messages are not counted as failed unless Meta returned a FAILED webhook status.</p>");
        }
        return html.toString();
    }

    private static String row(String label, String value) {
        return "<p><strong>" + escapeHtml(label) + ":</strong> " + escapeHtml(valueOrUnavailable(value)) + "</p>";
    }

    private static String format(java.time.LocalDateTime value) {
        return value == null ? "Unavailable" : value.format(DATE_TIME_FORMATTER) + " IST";
    }

    private static String valueOrUnavailable(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "Unavailable" : String.valueOf(value);
    }

    private static String safe(String value) { return value == null || value.isBlank() ? "Unknown Organization" : value; }

    private static String escapeHtml(String input) {
        return input == null ? "" : input.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
