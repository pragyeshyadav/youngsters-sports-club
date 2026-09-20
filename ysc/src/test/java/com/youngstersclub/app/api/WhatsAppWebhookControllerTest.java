package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngstersclub.app.service.WhatsAppMessageStatusStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WhatsAppWebhookControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void missingFullPayloadLoggingPropertyDefaultsToDisabled() {
        WhatsAppWebhookController controller =
                new WhatsAppWebhookController(mock(WhatsAppMessageStatusStore.class), objectMapper);

        assertFalse((Boolean) ReflectionTestUtils.getField(controller, "fullPayloadLoggingEnabled"));
    }

    @Test
    void sanitizedPayloadRedactsSecretsAndCustomerPhoneFields() throws Exception {
        WhatsAppWebhookController controller =
                new WhatsAppWebhookController(mock(WhatsAppMessageStatusStore.class), objectMapper);
        JsonNode sanitized = controller.sanitizePayload(objectMapper.readTree("""
                {
                  "access_token": "secret",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {"display_phone_number": "919999999999"},
                        "statuses": [{"id": "wamid.1", "status": "failed"}]
                      }
                    }]
                  }]
                }
                """));

        assertEquals("[REDACTED]", sanitized.path("access_token").asText());
        assertEquals("[REDACTED]", sanitized.at("/entry/0/changes/0/value/metadata/display_phone_number").asText());
        assertEquals("wamid.1", sanitized.at("/entry/0/changes/0/value/statuses/0/id").asText());
    }

    @Test
    void webhookProcessingContinuesWhenFullPayloadLoggingIsEnabled() throws Exception {
        WhatsAppMessageStatusStore store = mock(WhatsAppMessageStatusStore.class);
        WhatsAppWebhookController controller = new WhatsAppWebhookController(store, objectMapper);
        ReflectionTestUtils.setField(controller, "fullPayloadLoggingEnabled", true);
        JsonNode payload = objectMapper.readTree("{\"entry\":[]}");

        assertTrue(controller.receiveWebhook(payload).getStatusCode().is2xxSuccessful());
        verify(store).applyWebhookPayload(payload);
    }
}
