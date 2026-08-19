package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BrevoEmailServiceTest {

    private BrevoEmailService brevoEmailService;

    @BeforeEach
    void setUp() {
        brevoEmailService = new BrevoEmailService();
    }

    @Test
    void buildTournamentRegistrationNotificationHtmlRendersAllSectionsSafely() {
        String html = brevoEmailService.buildTournamentRegistrationNotificationHtml(
                "Rahul <Sharma>",
                "9876543210",
                List.of("Snooker", "Table Tennis"),
                List.of("Chess"),
                LocalDateTime.of(2026, 8, 19, 12, 30));

        assertTrue(html.contains("Rahul &lt;Sharma&gt;"));
        assertTrue(html.contains("9876543210"));
        assertTrue(html.contains("Successfully Registered Games"));
        assertTrue(html.contains("Snooker"));
        assertTrue(html.contains("Table Tennis"));
        assertTrue(html.contains("Already Registered"));
        assertTrue(html.contains("Chess"));
        assertTrue(html.contains("19 Aug 2026"));
        assertTrue(html.contains("IST"));
    }

    @Test
    void buildTournamentRegistrationNotificationHtmlHandlesMissingNameAndPhone() {
        String html = brevoEmailService.buildTournamentRegistrationNotificationHtml(
                " ",
                null,
                List.of("Snooker"),
                List.of(),
                LocalDateTime.of(2026, 8, 19, 12, 30));

        assertTrue(html.contains("Unknown"));
        assertTrue(html.contains("Not provided"));
        assertFalse(html.contains("Already Registered"));
    }

    @Test
    void sendTournamentRegistrationNotificationReturnsFalseWhenConfigurationMissing() {
        ReflectionTestUtils.setField(brevoEmailService, "tournamentRegistrationNotificationEmail", "pragyesh.yadav@gmail.com");
        ReflectionTestUtils.setField(brevoEmailService, "brevoApiKey", "");
        ReflectionTestUtils.setField(brevoEmailService, "senderEmail", "");

        boolean sent = brevoEmailService.sendTournamentRegistrationNotification(
                "Rahul Sharma",
                "9876543210",
                List.of("Snooker"),
                List.of("Chess"));

        assertFalse(sent);
    }

    @Test
    void sendTournamentRegistrationNotificationReturnsFalseWhenNoNewRegistrationsExist() {
        ReflectionTestUtils.setField(brevoEmailService, "tournamentRegistrationNotificationEmail", "pragyesh.yadav@gmail.com");
        ReflectionTestUtils.setField(brevoEmailService, "brevoApiKey", "test-key");
        ReflectionTestUtils.setField(brevoEmailService, "senderEmail", "sender@test.com");

        boolean sent = brevoEmailService.sendTournamentRegistrationNotification(
                "Rahul Sharma",
                "9876543210",
                List.of(),
                List.of("Chess"));

        assertFalse(sent);
    }
}
