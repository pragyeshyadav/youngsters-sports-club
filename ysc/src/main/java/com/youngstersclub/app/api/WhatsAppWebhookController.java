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

@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

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
            return ResponseEntity.status(403).body("Forbidden");
        }
        return ResponseEntity.ok(challenge == null ? "" : challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody(required = false) JsonNode payload) {
        whatsAppMessageStatusStore.applyWebhookPayload(payload);
        return ResponseEntity.ok().build();
    }
}
