package com.youngstersclub.app.dto;

import java.time.LocalDateTime;
import java.util.List;

public class WhatsappTemplateExecutionResultDto {
    private final String templateName;
    private final boolean dryRun;
    private final LocalDateTime executionTime;
    private final int totalCustomersScanned;
    private final int eligibleCustomers;
    private final int skippedCustomers;
    private final int successfulMessages;
    private final int failedMessages;
    private final List<WhatsappTemplateExecutionRecipientDto> recipients;

    public WhatsappTemplateExecutionResultDto(
            String templateName,
            boolean dryRun,
            LocalDateTime executionTime,
            int totalCustomersScanned,
            int eligibleCustomers,
            int skippedCustomers,
            int successfulMessages,
            int failedMessages,
            List<WhatsappTemplateExecutionRecipientDto> recipients) {
        this.templateName = templateName;
        this.dryRun = dryRun;
        this.executionTime = executionTime;
        this.totalCustomersScanned = totalCustomersScanned;
        this.eligibleCustomers = eligibleCustomers;
        this.skippedCustomers = skippedCustomers;
        this.successfulMessages = successfulMessages;
        this.failedMessages = failedMessages;
        this.recipients = recipients == null ? List.of() : recipients;
    }

    public String getTemplateName() {
        return templateName;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public int getTotalCustomersScanned() {
        return totalCustomersScanned;
    }

    public int getEligibleCustomers() {
        return eligibleCustomers;
    }

    public int getSkippedCustomers() {
        return skippedCustomers;
    }

    public int getSuccessfulMessages() {
        return successfulMessages;
    }

    public int getFailedMessages() {
        return failedMessages;
    }

    public List<WhatsappTemplateExecutionRecipientDto> getRecipients() {
        return recipients;
    }
}
