package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.service.AdminAnalyticsService;
import com.youngstersclub.app.service.AdminNotificationBroadcastService;
import com.youngstersclub.app.service.ConsumableService;
import com.youngstersclub.app.service.WhatsAppTemplateExecutionService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private AdminAnalyticsService adminAnalyticsService;
    @Mock private ConsumableService consumableService;
    @Mock private WhatsAppTemplateExecutionService whatsAppTemplateExecutionService;
    @Mock private AdminNotificationBroadcastService adminNotificationBroadcastService;

    @InjectMocks private AdminController adminController;

    @Test
    void getMonthlyEarningsPassesActorHeaderToScopedService() {
        AdminMonthlyEarningsDto dto = new AdminMonthlyEarningsDto(
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(90),
                BigDecimal.valueOf(70),
                Map.of("Area 7 Arena", BigDecimal.valueOf(70)),
                BigDecimal.valueOf(20),
                BigDecimal.valueOf(10));
        when(adminAnalyticsService.getMonthlyEarnings(8, 2026, "admin@test.com")).thenReturn(dto);

        ResponseEntity<AdminMonthlyEarningsDto> response =
                adminController.getMonthlyEarnings(8, 2026, "admin@test.com");

        assertEquals(dto, response.getBody());
        verify(adminAnalyticsService).getMonthlyEarnings(8, 2026, "admin@test.com");
    }
}
