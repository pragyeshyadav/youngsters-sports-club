package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;

public interface WhatsAppTemplateExecutor {

    String getTemplateName();

    WhatsappTemplateExecutionResultDto execute(boolean isDryRun);

    default WhatsappTemplateExecutionResultDto executeForOrganization(Long organizationId, boolean isDryRun) {
        return execute(isDryRun);
    }
}
