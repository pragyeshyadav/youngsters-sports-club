package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchOptionDto;
import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.WhatsAppMessageStatusPageDto;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminWhatsAppMessageStatusServiceTest {

    @Mock private OrganizationContextService organizationContextService;
    @Mock private WhatsAppMessageStatusStore whatsAppMessageStatusStore;

    @InjectMocks private AdminWhatsAppMessageStatusService service;

    @Test
    void getTodayStatusesUsesCurrentOrganizationAndBranchContext() {
        when(organizationContextService.resolveContext("admin@test.com"))
                .thenReturn(buildContext("ADMIN", 7L, "The Cue Society", 9L, "Satna"));
        when(whatsAppMessageStatusStore.getMessagesForOrganizationOnDate(
                7L,
                9L,
                com.youngstersclub.app.util.TimeUtil.nowIST().toLocalDate(),
                0,
                20))
                .thenReturn(new WhatsAppMessageStatusPageDto(List.of(), 0, 20, false));

        WhatsAppMessageStatusPageDto response = service.getTodayStatuses("admin@test.com", null);

        assertEquals(0, response.getMessages().size());
        verify(whatsAppMessageStatusStore).getMessagesForOrganizationOnDate(
                7L,
                9L,
                com.youngstersclub.app.util.TimeUtil.nowIST().toLocalDate(),
                0,
                20);
    }

    @Test
    void getTodayStatusesRejectsNonAdminRole() {
        when(organizationContextService.resolveContext("customer@test.com"))
                .thenReturn(buildContext("CUSTOMER", 7L, "The Cue Society", 9L, "Satna"));

        SecurityException exception = assertThrows(
                SecurityException.class,
                () -> service.getTodayStatuses("customer@test.com", 0));

        assertEquals("This view is available only for admin users", exception.getMessage());
    }

    private OrganizationContextDto buildContext(
            String role,
            Long organizationId,
            String organizationName,
            Long branchId,
            String branchName) {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole(role);
        context.setCurrentOrganization(new OrganizationOptionDto(organizationId, organizationName));
        context.setCurrentBranch(new BranchOptionDto(branchId, branchName));
        return context;
    }
}
