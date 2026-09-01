package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.WhatsAppMessageStatusPageDto;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.util.TimeUtil;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class AdminWhatsAppMessageStatusService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final OrganizationContextService organizationContextService;
    private final WhatsAppMessageStatusStore whatsAppMessageStatusStore;

    public AdminWhatsAppMessageStatusService(
            OrganizationContextService organizationContextService,
            WhatsAppMessageStatusStore whatsAppMessageStatusStore) {
        this.organizationContextService = organizationContextService;
        this.whatsAppMessageStatusStore = whatsAppMessageStatusStore;
    }

    public WhatsAppMessageStatusPageDto getTodayStatuses(String actorEmail, Integer page) {
        OrganizationContextDto context = organizationContextService.resolveContext(actorEmail);
        if (context == null || context.getCurrentOrganization() == null || context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current organization and branch context are required");
        }

        String currentRole = context.getCurrentRole() == null ? "" : context.getCurrentRole().trim().toUpperCase(Locale.ROOT);
        if (!UserRole.ADMIN.name().equals(currentRole) && !UserRole.SUPER_ADMIN.name().equals(currentRole)) {
            throw new SecurityException("This view is available only for admin users");
        }

        return whatsAppMessageStatusStore.getMessagesForOrganizationOnDate(
                context.getCurrentOrganization().getId(),
                context.getCurrentBranch().getId(),
                TimeUtil.nowIST().toLocalDate(),
                page == null ? 0 : Math.max(page, 0),
                DEFAULT_PAGE_SIZE);
    }
}
