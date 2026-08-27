package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class WhatsAppTemplateExecutionService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppTemplateExecutionService.class);

    private final Map<String, WhatsAppTemplateExecutor> executorsByTemplateName;
    private final OrganizationContextService organizationContextService;

    public WhatsAppTemplateExecutionService(
            List<WhatsAppTemplateExecutor> executors,
            OrganizationContextService organizationContextService) {
        this.executorsByTemplateName = executors.stream()
                .collect(Collectors.toMap(
                        executor -> normalizeTemplateName(executor.getTemplateName()),
                        Function.identity(),
                        (left, right) -> left));
        this.organizationContextService = organizationContextService;
    }

    @Async
    public void triggerTemplateExecution(String templateName, boolean isDryRun) {
        try {
            executeTemplate(templateName, isDryRun);
        } catch (Exception ex) {
            log.error(
                    "Manual WhatsApp template trigger failed. templateName: {}, mode: {}. Reason: {}",
                    templateName,
                    isDryRun ? "DRY RUN" : "ACTUAL RUN",
                    ex.getMessage(),
                    ex);
        }
    }

    @Async
    public void triggerTemplateExecutionForCurrentOrganization(
            String templateName,
            boolean isDryRun,
            String actorEmail) {
        try {
            executeTemplateForCurrentOrganization(templateName, isDryRun, actorEmail);
        } catch (Exception ex) {
            log.error(
                    "Manual WhatsApp template trigger failed for selected organization. templateName: {}, mode: {}, actorEmail: {}. Reason: {}",
                    templateName,
                    isDryRun ? "DRY RUN" : "ACTUAL RUN",
                    actorEmail,
                    ex.getMessage(),
                    ex);
        }
    }

    public WhatsappTemplateExecutionResultDto executeTemplate(String templateName, boolean isDryRun) {
        return resolveExecutor(templateName).execute(isDryRun);
    }

    public WhatsappTemplateExecutionResultDto executeTemplateForCurrentOrganization(
            String templateName,
            boolean isDryRun,
            String actorEmail) {
        Long organizationId = resolveCurrentOrganizationId(actorEmail);
        return resolveExecutor(templateName).executeForOrganization(organizationId, isDryRun);
    }

    protected Long resolveCurrentOrganizationId(String actorEmail) {
        var context = organizationContextService.resolveContext(actorEmail);
        if (context == null || context.getCurrentOrganization() == null || context.getCurrentOrganization().getId() == null) {
            throw new SecurityException("Current organization context is required");
        }
        return context.getCurrentOrganization().getId();
    }

    private WhatsAppTemplateExecutor resolveExecutor(String templateName) {
        String normalizedTemplateName = normalizeTemplateName(templateName);
        WhatsAppTemplateExecutor executor = executorsByTemplateName.get(normalizedTemplateName);
        if (executor == null) {
            throw new IllegalArgumentException("Unsupported WhatsApp template: " + templateName);
        }
        return executor;
    }

    private String normalizeTemplateName(String templateName) {
        return templateName == null ? "" : templateName.trim().toLowerCase(Locale.ROOT);
    }
}
