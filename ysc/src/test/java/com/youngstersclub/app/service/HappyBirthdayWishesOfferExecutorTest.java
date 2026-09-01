package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import com.youngstersclub.app.entity.Organization;
import com.youngstersclub.app.repository.ChildRepository;
import com.youngstersclub.app.repository.OrganizationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HappyBirthdayWishesOfferExecutorTest {

    @Mock private ChildRepository childRepository;
    @Mock private WhatsAppService whatsAppService;
    @Mock private BrevoEmailService brevoEmailService;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationSummaryRecipientService organizationSummaryRecipientService;

    @InjectMocks private HappyBirthdayWishesOfferExecutor executor;

    @Test
    void executeDryRunBuildsSeparateOrganizationSummaries() {
        when(childRepository.findBirthdayChildrenByMonthAndDay(any(Integer.class), any(Integer.class)))
                .thenReturn(List.of(
                        birthdayChild(1L, "YSC", 10, "Rahul", "9999999999", "Aarav", "Satna"),
                        birthdayChild(2L, "Area 7", 11, "Prince", "8888888888", "Aanya", "Rewa")));
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(1L))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "youngsterssportsclub@gmail.com"));
        when(organizationSummaryRecipientService.resolveRecipientsForOrganization(2L))
                .thenReturn(List.of("pragyesh.yadav@gmail.com", "area7shrinet@gmail.com"));
        when(organizationRepository.findByIdAndIsActiveTrue(1L)).thenReturn(Optional.of(organization(1L, "ysc@test.com")));
        when(organizationRepository.findByIdAndIsActiveTrue(2L)).thenReturn(Optional.of(organization(2L, "area7@test.com")));
        when(brevoEmailService.sendHappyBirthdayWishesSummaryEmail(any(), anyList(), any())).thenReturn(0);

        WhatsappTemplateExecutionResultDto result = executor.execute(true);

        assertEquals(2, result.getTotalCustomersScanned());
        assertEquals(2, result.getEligibleCustomers());
        assertEquals(2, result.getSuccessfulMessages());
        assertEquals(0, result.getFailedMessages());
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(1L);
        verify(organizationSummaryRecipientService).resolveRecipientsForOrganization(2L);
        verify(brevoEmailService).sendHappyBirthdayWishesSummaryEmail(any(), anyList(), org.mockito.ArgumentMatchers.eq("ysc@test.com"));
        verify(brevoEmailService).sendHappyBirthdayWishesSummaryEmail(any(), anyList(), org.mockito.ArgumentMatchers.eq("area7@test.com"));
        verify(whatsAppService, never()).sendHappyBirthdayWishesOfferMessage(any(), any(), any(), any(), any(), any(Integer.class), any());
    }

    private Organization organization(Long id, String email) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setEmail(email);
        organization.setIsActive(true);
        return organization;
    }

    private ChildRepository.BirthdayChildProjection birthdayChild(
            Long organizationId,
            String organizationName,
            Integer parentUserId,
            String parentName,
            String parentPhone,
            String childName,
            String baseBranchName) {
        return new ChildRepository.BirthdayChildProjection() {
            @Override
            public Long getChildId() {
                return 1L;
            }

            @Override
            public String getChildName() {
                return childName;
            }

            @Override
            public Integer getParentUserId() {
                return parentUserId;
            }

            @Override
            public String getParentName() {
                return parentName;
            }

            @Override
            public String getParentPhone() {
                return parentPhone;
            }

            @Override
            public Long getOrganizationId() {
                return organizationId;
            }

            @Override
            public String getOrganizationName() {
                return organizationName;
            }

            @Override
            public Long getBaseBranchId() {
                return 101L;
            }

            @Override
            public String getBaseBranchName() {
                return baseBranchName;
            }
        };
    }
}
