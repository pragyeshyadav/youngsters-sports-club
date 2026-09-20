package com.youngstersclub.app.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.youngstersclub.app.service.WhatsAppMessageStatusStore;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppMessageStatusStore whatsAppMessageStatusStore;
    private final ObjectMapper objectMapper;

    @Value("${whatsapp.webhook.verify-token:}")
    private String verifyToken;

    @Value("${whatsapp.webhook.full-payload-logging-enabled:false}")
    private boolean fullPayloadLoggingEnabled;

    public WhatsAppWebhookController(WhatsAppMessageStatusStore whatsAppMessageStatusStore) {
        this(whatsAppMessageStatusStore, new ObjectMapper());
    }

    @Autowired
    public WhatsAppWebhookController(
            WhatsAppMessageStatusStore whatsAppMessageStatusStore,
            ObjectMapper objectMapper) {
        this.whatsAppMessageStatusStore = whatsAppMessageStatusStore;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equalsIgnoreCase(mode) || verifyToken == null || verifyToken.isBlank() || !verifyToken.equals(token)) {
            log.warn("WhatsApp webhook verification rejected. modePresent: {}, tokenPresent: {}, challengePresent: {}",
                    mode != null, token != null, challenge != null);
            return ResponseEntity.status(403).body("Forbidden");
        }
        log.info("WhatsApp webhook verification succeeded");
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody(required = false) JsonNode payload) {
        log.info("WhatsApp webhook POST received. payloadPresent: {}, entryCount: {}, statusCount: {}",
                payload != null,
                countEntries(payload),
                countStatuses(payload));
        logFullPayloadIfEnabled(payload);
        whatsAppMessageStatusStore.applyWebhookPayload(payload);
        return ResponseEntity.ok().build();
    }

    protected void logFullPayloadIfEnabled(JsonNode payload) {
        if (!fullPayloadLoggingEnabled || payload == null) {
            return;
        }
        try {
            log.info("WhatsApp webhook sanitized payload: {}",
                    objectMapper.writeValueAsString(sanitizePayload(payload)));
        } catch (Exception ex) {
            log.warn("Unable to produce sanitized WhatsApp webhook diagnostic log. Reason: {}", ex.getMessage());
        }
    }

    protected JsonNode sanitizePayload(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sanitized.set(field.getKey(), isSensitiveField(field.getKey())
                        ? objectMapper.getNodeFactory().textNode("[REDACTED]")
                        : sanitizePayload(field.getValue()));
            }
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                sanitized.add(sanitizePayload(child));
            }
            return sanitized;
        }
        return node;
    }

    protected boolean isSensitiveField(String fieldName) {
        String field = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        return field.contains("token")
                || field.contains("authorization")
                || field.contains("phone")
                || field.equals("wa_id")
                || field.equals("recipient_id")
                || field.equals("from")
                || field.equals("to");
    }

    private int countEntries(JsonNode payload) {
        return payload != null && payload.path("entry").isArray() ? payload.path("entry").size() : 0;
    }

    private int countStatuses(JsonNode payload) {
        if (payload == null || !payload.path("entry").isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode entry : payload.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                if (change.path("value").path("statuses").isArray()) {
                    count += change.path("value").path("statuses").size();
                }
            }
        }
        return count;
    }
}
