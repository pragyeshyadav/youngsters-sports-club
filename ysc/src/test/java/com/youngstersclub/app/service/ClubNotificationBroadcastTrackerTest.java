package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
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

    @Test
    void finalizationClaimUsesAtomicRedisScriptAndAcceptsOnlyTheWinningResult() {
        StringRedisTemplate redisTemplate = org.mockito.Mockito.mock(StringRedisTemplate.class);
        ClubNotificationBroadcastTracker redisTracker =
                new ClubNotificationBroadcastTracker(redisTemplate, new ObjectMapper(), null);

        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("ysc:whatsapp:club-broadcast:v1:broadcast-1:state")),
                eq("finalized"),
                eq("false"),
                eq("true")))
                .thenReturn(1L)
                .thenReturn(0L);

        assertTrue(redisTracker.claimFinalization("broadcast-1"));
        assertFalse(redisTracker.claimFinalization("broadcast-1"));
    }

    @Test
    void deadlineExtensionReturnsFalseWhenValkeyUpdateFails() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Valkey unavailable"));

        ClubNotificationBroadcastTracker redisTracker =
                new ClubNotificationBroadcastTracker(redisTemplate, new ObjectMapper(), null);

        assertFalse(redisTracker.ensureDeadlineAtLeast("broadcast-1", LocalDateTime.now().plusMinutes(15)));
    }

    @Test
    void successfulDeadlineExtensionUsesAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(java.util.Set.of());
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        ClubNotificationBroadcastTracker redisTracker =
                new ClubNotificationBroadcastTracker(redisTemplate, new ObjectMapper(), null);

        assertTrue(redisTracker.ensureDeadlineAtLeast("broadcast-1", LocalDateTime.now().plusMinutes(15)));
    }
}
