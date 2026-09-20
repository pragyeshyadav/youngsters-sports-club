package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.OrganizationOptionDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.DefaultTypedTuple;

class AdminWhatsAppRecipientHealthServiceTest {

    @Test
    void readsOnlyTheSelectedOrganizationAndStatus() {
        OrganizationContextService contextService = mock(OrganizationContextService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ZSetOperations<String, String> zsets = mock(ZSetOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        WhatsAppRecipientHealthService healthService = mock(WhatsAppRecipientHealthService.class);
        OrganizationContextDto context = adminContext(7L);
        when(contextService.resolveContext("admin@example.com")).thenReturn(context);
        when(healthService.statusIndexKey(7L, "FAILED")).thenReturn("health-index:7:FAILED");
        when(healthService.key(7L, "919999999999")).thenReturn("health:7:919999999999");
        when(redisTemplate.opsForZSet()).thenReturn(zsets);
        when(redisTemplate.opsForHash()).thenReturn(hashes);
        TypedTuple<String> tuple = new DefaultTypedTuple<>("919999999999", 100D);
        when(zsets.reverseRangeWithScores("health-index:7:FAILED", 0, 99))
                .thenReturn(Set.of(tuple));
        Map<Object, Object> record = new LinkedHashMap<>();
        record.put("lastStatus", "FAILED");
        record.put("customerName", "Rahul Sharma");
        record.put("customerPhone", "9999999999");
        when(hashes.entries("health:7:919999999999")).thenReturn(record);
        when(zsets.zCard("health-index:7:FAILED")).thenReturn(1L);

        AdminWhatsAppRecipientHealthService service = new AdminWhatsAppRecipientHealthService(
                contextService, redisTemplate, healthService);

        var page = service.getHealth("admin@example.com", "FAILED", null);

        assertEquals(1, page.customers().size());
        assertEquals("Rahul Sharma", page.customers().get(0).customerName());
        assertEquals("9999999999", page.customers().get(0).phoneNumber());
    }

    @Test
    void rejectsCustomerRoleBeforeReadingValkey() {
        OrganizationContextService contextService = mock(OrganizationContextService.class);
        OrganizationContextDto context = adminContext(7L);
        context.setCurrentRole("CUSTOMER");
        when(contextService.resolveContext("customer@example.com")).thenReturn(context);

        AdminWhatsAppRecipientHealthService service = new AdminWhatsAppRecipientHealthService(
                contextService,
                mock(StringRedisTemplate.class),
                mock(WhatsAppRecipientHealthService.class));

        assertThrows(SecurityException.class,
                () -> service.getHealth("customer@example.com", "READ", null));
    }

    @Test
    void rejectsInvalidStatusAndCursor() {
        AdminWhatsAppRecipientHealthService service = new AdminWhatsAppRecipientHealthService(
                mock(OrganizationContextService.class),
                mock(StringRedisTemplate.class),
                mock(WhatsAppRecipientHealthService.class));

        assertThrows(IllegalArgumentException.class, () -> service.normalizeStatus("ALL"));
        assertThrows(IllegalArgumentException.class, () -> service.parseCursor("not-a-cursor"));
    }

    private OrganizationContextDto adminContext(Long organizationId) {
        OrganizationContextDto context = new OrganizationContextDto();
        context.setCurrentRole("ADMIN");
        OrganizationOptionDto organization = new OrganizationOptionDto();
        organization.setId(organizationId);
        context.setCurrentOrganization(organization);
        return context;
    }
}
