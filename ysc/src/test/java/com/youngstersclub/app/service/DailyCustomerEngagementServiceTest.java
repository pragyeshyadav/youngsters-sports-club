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
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.repository.DailyCustomerVisitRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyCustomerEngagementServiceTest {

    @Mock private DailyCustomerVisitRepository dailyCustomerVisitRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BrevoEmailService brevoEmailService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationSummaryRecipientService organizationSummaryRecipientService;

    @InjectMocks private DailyCustomerEngagementService dailyCustomerEngagementService;

    @Test
    void processDailyWhatsappNotificationsUsesPreAggregatedVisitsAndBuildsRecipientsPerOrganization() {
        Organization ysc = organization(1L, "ysc@test.com");
        Organization area7 = organization(2L, "area7@test.com");
        when(dailyCustomerVisitRepository.findDailyVisitedCustomersByOrganization(any()))
                .thenReturn(List.of(
                        new DailyVisitedOrganizationDto(10, "Rahul", "9999999999", 1L, "YSC", null, "Satna, Rewa"),
                        new DailyVisitedOrganizationDto(11, "Prince", "", 1L, "YSC", null, "Organization-wide"),
                        new DailyVisitedOrganizationDto(12, "Aryan", "8888888888", 2L, "Area 7", null, "Rewa")));
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(1L))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"));
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(2L))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "area7shrinet@gmail.com"));
        when(organizationRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(ysc));
        when(organizationRepository.findByIdAndIsActiveTrue(2L)).thenReturn(Optional.of(area7));
        when(brevoEmailService.sendDailyVisitSummaryEmail(any(), anyList(), any())).thenReturn(0);

        WhatsappTemplateExecutionResultDto result = dailyCustomerEngagementService.processDailyWhatsappNotifications(true);

        assertEquals(3, result.getTotalCustomersScanned());
        assertEquals(2, result.getEligibleCustomers());
        assertEquals(1, result.getFailedMessages());
        assertEquals(2, result.getSuccessfulMessages());
        assertEquals(2, result.getRecipients().size());
        WhatsappTemplateExecutionRecipientDto recipient = result.getRecipients().get(0);
        assertEquals("Rahul", recipient.getName());
        assertEquals("Satna, Rewa", recipient.getBranchName());
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(1L);
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(2L);
        verify(brevoEmailService).sendDailyVisitSummaryEmail(any(), anyList(), org.mockito.ArgumentMatchers.eq("ysc@test.com"));
        verify(brevoEmailService).sendDailyVisitSummaryEmail(any(), anyList(), org.mockito.ArgumentMatchers.eq("area7@test.com"));
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

    private Organization organization(Long id, String email) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setEmail(email);
        organization.setIsActive(true);
        return organization;
    }
}
