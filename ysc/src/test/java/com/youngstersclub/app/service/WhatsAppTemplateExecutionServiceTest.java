package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import com.youngstersclub.app.dto.WhatsappTemplateExecutionResultDto;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhatsAppTemplateExecutionServiceTest {

    @Mock private OrganizationContextService organizationContextService;
    @Mock private WhatsAppTemplateExecutor executor;

    @Test
    void executeTemplateForCurrentOrganizationUsesSelectedOrganizationContext() {
        when(executor.getTemplateName()).thenReturn("happy_birthday_wishes_offer");
        WhatsAppTemplateExecutionService service =
                new WhatsAppTemplateExecutionService(List.of(executor), organizationContextService);

        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentOrganization(new OrganizationOptionDto(7L, "Headquarter City Center Snooker Club"));
        when(organizationContextService.resolveContext("admin@test.com")).thenReturn(context);

        WhatsappTemplateExecutionResultDto expected = new WhatsappTemplateExecutionResultDto(
                "happy_birthday_wishes_offer",
                true,
                LocalDateTime.of(2026, 8, 27, 10, 0),
                0,
                0,
                0,
                0,
                0,
                List.of());
        when(executor.executeForOrganization(7L, true)).thenReturn(expected);

        WhatsappTemplateExecutionResultDto response =
                service.executeTemplateForCurrentOrganization("happy_birthday_wishes_offer", true, "admin@test.com");

        assertSame(expected, response);
        verify(executor).executeForOrganization(7L, true);
    }
}
