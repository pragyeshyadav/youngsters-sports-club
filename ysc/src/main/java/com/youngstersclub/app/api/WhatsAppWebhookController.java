package com.youngstersclub.app.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.youngstersclub.app.service.WhatsAppMessageStatusStore;
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

    @Value("${whatsapp.webhook.verify-token:}")
    private String verifyToken;

    public WhatsAppWebhookController(WhatsAppMessageStatusStore whatsAppMessageStatusStore) {
        this.whatsAppMessageStatusStore = whatsAppMessageStatusStore;
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
        whatsAppMessageStatusStore.applyWebhookPayload(payload);
        return ResponseEntity.ok().build();
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
