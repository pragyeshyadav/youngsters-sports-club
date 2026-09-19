package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClubNotificationBroadcastTrackerTest {

    private final ClubNotificationBroadcastTracker tracker =
            new ClubNotificationBroadcastTracker(null, new ObjectMapper(), null);

    @Test
    void statusOrderingDoesNotAllowOlderSuccessfulEventsToRegress() {
        assertTrue(tracker.shouldReplaceStatus(null, "SENT"));
        assertTrue(tracker.shouldReplaceStatus("SENT", "DELIVERED"));
        assertTrue(tracker.shouldReplaceStatus("DELIVERED", "READ"));
        assertFalse(tracker.shouldReplaceStatus("READ", "SENT"));
        assertTrue(tracker.shouldReplaceStatus("DELIVERED", "FAILED"));
        assertFalse(tracker.shouldReplaceStatus("FAILED", "READ"));
    }

    @Test
    void statusRanksKeepTerminalStatusesDeterministic() {
        assertEquals(0, tracker.statusRank("unknown"));
        assertEquals(1, tracker.statusRank("sent"));
        assertEquals(2, tracker.statusRank("DELIVERED"));
        assertEquals(3, tracker.statusRank("read"));
        assertEquals(4, tracker.statusRank("FAILED"));
    }
}
