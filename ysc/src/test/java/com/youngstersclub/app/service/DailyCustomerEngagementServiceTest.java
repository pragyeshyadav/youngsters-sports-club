package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.DailyVisitedOrganizationDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionRecipientDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.enums.UserRole;
import com.youngstersclub.app.repository.DailyCustomerVisitRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyCustomerEngagementServiceTest {

    @Mock private DailyCustomerVisitRepository dailyCustomerVisitRepository;
    @Mock private UserRepository userRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BrevoEmailService brevoEmailService;

    @InjectMocks private DailyCustomerEngagementService dailyCustomerEngagementService;

    @Test
    void processDailyWhatsappNotificationsUsesPreAggregatedVisitsAndBuildsRecipients() {
        when(dailyCustomerVisitRepository.findDailyVisitedCustomersByOrganization(any()))
                .thenReturn(List.of(
                        new DailyVisitedOrganizationDto(10, "Rahul", "9999999999", 1L, "YSC", null, "Satna, Rewa"),
                        new DailyVisitedOrganizationDto(11, "Prince", "", 1L, "YSC", null, "Organization-wide")));
        when(userRepository.findByRoleInAndIsActiveTrue(List.of(UserRole.ADMIN, UserRole.SUPER_ADMIN)))
                .thenReturn(List.of());
        when(brevoEmailService.sendDailyVisitSummaryEmail(any(), anyList())).thenReturn(0);

        WhatsappTemplateExecutionResultDto result = dailyCustomerEngagementService.processDailyWhatsappNotifications(true);

        assertEquals(2, result.getTotalCustomersScanned());
        assertEquals(1, result.getEligibleCustomers());
        assertEquals(1, result.getFailedMessages());
        assertEquals(1, result.getSuccessfulMessages());
        assertEquals(1, result.getRecipients().size());
        WhatsappTemplateExecutionRecipientDto recipient = result.getRecipients().get(0);
        assertEquals("Rahul", recipient.getName());
        assertEquals("Satna, Rewa", recipient.getBranchName());
        verify(whatsAppService, never()).sendDailyVisitThankYouMessage(any(), any());
    }

    @Test
    void buildRecipientSummaryPreservesOrganizationAndBranchNames() {
        WhatsappTemplateExecutionRecipientDto recipient = dailyCustomerEngagementService.buildRecipientSummary(
                new DailyVisitedOrganizationDto(10, "Rahul", "9999999999", 1L, "YSC", null, "Satna"));

        assertEquals(10, recipient.getUserId());
        assertEquals("YSC", recipient.getOrganizationName());
        assertEquals("Satna", recipient.getBranchName());
        assertEquals("VISITED TODAY", recipient.getStatus());
    }
}
