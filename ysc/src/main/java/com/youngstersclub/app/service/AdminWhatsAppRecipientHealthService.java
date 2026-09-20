package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.OrganizationContextDto;
import com.youngstersclub.app.dto.WhatsAppRecipientHealthCustomerDto;
import com.youngstersclub.app.dto.WhatsAppRecipientHealthPageDto;
import com.youngstersclub.app.enums.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

@Service
public class AdminWhatsAppRecipientHealthService {

    private static final Logger log = LoggerFactory.getLogger(AdminWhatsAppRecipientHealthService.class);
    private static final int PAGE_SIZE = 20;
    private static final int FETCH_SIZE = 100;
    private static final Pattern CURSOR_PATTERN = Pattern.compile("\\d+");

    private final OrganizationContextService organizationContextService;
    private final StringRedisTemplate redisTemplate;
    private final WhatsAppRecipientHealthService recipientHealthService;

    public AdminWhatsAppRecipientHealthService(
            OrganizationContextService organizationContextService,
            StringRedisTemplate redisTemplate,
            WhatsAppRecipientHealthService recipientHealthService) {
        this.organizationContextService = organizationContextService;
        this.redisTemplate = redisTemplate;
        this.recipientHealthService = recipientHealthService;
    }

    public WhatsAppRecipientHealthPageDto getHealth(String actorEmail, String status, String cursor) {
        OrganizationContextDto context = organizationContextService.resolveContext(actorEmail);
        authorize(context);
        String normalizedStatus = normalizeStatus(status);
        int offset = parseCursor(cursor);
        Long organizationId = context.getCurrentOrganization().getId();
        String indexKey = recipientHealthService.statusIndexKey(organizationId, normalizedStatus);
        List<WhatsAppRecipientHealthCustomerDto> customers = new ArrayList<>();
        int inspected = offset;
        boolean hasMore = false;

        try {
            while (customers.size() < PAGE_SIZE) {
                Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet()
                        .reverseRangeWithScores(indexKey, inspected, inspected + FETCH_SIZE - 1);
                if (tuples == null || tuples.isEmpty()) {
                    break;
                }
                int tupleCount = tuples.size();
                for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                    inspected++;
                    String phone = tuple == null ? null : tuple.getValue();
                    HealthRecord record = loadRecord(organizationId, phone);
                    if (record == null || !normalizedStatus.equals(record.status())) {
                        removeStaleIndexMember(indexKey, phone);
                        continue;
                    }
                    customers.add(new WhatsAppRecipientHealthCustomerDto(
                            blankAsFallback(record.customerName(), "-"),
                            blankAsFallback(record.customerPhone(), phone)));
                    if (customers.size() == PAGE_SIZE) {
                        break;
                    }
                }
                if (tupleCount < FETCH_SIZE) {
                    break;
                }
                if (customers.size() == PAGE_SIZE) {
                    hasMore = true;
                    break;
                }
            }
            if (!hasMore) {
                hasMore = hasAnyRemaining(indexKey, inspected);
            }
        } catch (Exception ex) {
            log.warn("Unable to load WhatsApp recipient health. organizationId: {}, status: {}, reason: {}",
                    organizationId, normalizedStatus, ex.getMessage());
            throw new IllegalStateException("Unable to load WhatsApp recipient health", ex);
        }

        return new WhatsAppRecipientHealthPageDto(
                customers,
                hasMore ? String.valueOf(inspected) : null,
                hasMore);
    }

    protected void authorize(OrganizationContextDto context) {
        if (context == null || context.getCurrentOrganization() == null) {
            throw new IllegalArgumentException("Current organization context is required");
        }
        String role = context.getCurrentRole() == null
                ? ""
                : context.getCurrentRole().trim().toUpperCase(Locale.ROOT);
        if (!UserRole.ADMIN.name().equals(role) && !UserRole.SUPER_ADMIN.name().equals(role)) {
            throw new SecurityException("This view is available only for admin users");
        }
    }

    protected String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("READ", "DELIVERED", "SENT", "FAILED").contains(normalized)) {
            throw new IllegalArgumentException("Status must be READ, DELIVERED, SENT, or FAILED");
        }
        return normalized;
    }

    protected int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        if (!CURSOR_PATTERN.matcher(cursor.trim()).matches()) {
            throw new IllegalArgumentException("Invalid health cursor");
        }
        return Math.max(0, Integer.parseInt(cursor.trim()));
    }

    protected HealthRecord loadRecord(Long organizationId, String normalizedPhone) {
        if (organizationId == null || normalizedPhone == null || normalizedPhone.isBlank()) {
            return null;
        }
        var values = redisTemplate.opsForHash().entries(recipientHealthService.key(organizationId, normalizedPhone));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new HealthRecord(
                value(values.get("lastStatus")),
                value(values.get("customerName")),
                value(values.get("customerPhone")));
    }

    private boolean hasAnyRemaining(String indexKey, int offset) {
        Long count = redisTemplate.opsForZSet().zCard(indexKey);
        return count != null && count > offset;
    }

    private void removeStaleIndexMember(String indexKey, String phone) {
        if (phone != null) {
            redisTemplate.opsForZSet().remove(indexKey, phone);
        }
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankAsFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    protected record HealthRecord(String status, String customerName, String customerPhone) {
    }
}
