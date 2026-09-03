package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.PlayerPerformanceResponseDto;
import com.youngstersclub.app.entity.User;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerPerformanceServiceTest {
    @Mock private FrameRepository frameRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlayerPerformanceCache cache;

    private TestPlayerPerformanceService service;

    @BeforeEach
    void setUp() {
        service = new TestPlayerPerformanceService(frameRepository, userRepository, cache);
    }

    @Test
    void calculatesWinRateWithOneDecimalAndHandlesZeroFrames() {
        assertEquals(new BigDecimal("60.9"), service.exposeCalculateWinRate(112, 184));
        assertEquals(new BigDecimal("0.0"), service.exposeCalculateWinRate(0, 0));
    }

    @Test
    void buildsFiveRowWindowAroundCurrentPlayer() {
        PlayerPerformanceService.Stat aboveOne = stat(1, "Above One", 126, 74, 200);
        PlayerPerformanceService.Stat aboveTwo = stat(2, "Above Two", 124, 76, 200);
        PlayerPerformanceService.Stat current = stat(3, "Current", 60, 40, 100);
        PlayerPerformanceService.Stat belowOne = stat(4, "Below One", 59, 41, 100);
        PlayerPerformanceService.Stat belowTwo = stat(5, "Below Two", 58, 42, 100);
        PlayerPerformanceService.Stat belowThree = stat(6, "Below Three", 57, 43, 100);

        var result = service.exposeBuildCompetitorWindow(
                List.of(belowThree, belowTwo, belowOne, current, aboveTwo, aboveOne), current);

        assertEquals(List.of("Above One", "Above Two", "Current", "Below One", "Below Two"),
                result.stream().map(PlayerPerformanceResponseDto.CompetitorDto::displayName).toList());
        assertTrue(result.get(2).currentPlayer());
    }

    @Test
    void excludesPlayersBelowMinimumFromCompetitorComparison() {
        PlayerPerformanceService.Stat current = stat(14, "Current", 6, 3, 5);

        assertTrue(service.exposeBuildCompetitorWindow(List.of(current), current).isEmpty());
    }

    @Test
    void calculatesGlobalSnapshotFromRegisteredEndedParticipantsAndRecentForm() {
        FrameRepository.GlobalPlayerPerformanceProjection summary = org.mockito.Mockito.mock(
                FrameRepository.GlobalPlayerPerformanceProjection.class);
        when(summary.getUserId()).thenReturn(14);
        when(summary.getDisplayName()).thenReturn("Pragyesh");
        when(summary.getProfileImageUrl()).thenReturn("https://example.test/profile.png");
        when(summary.getTotalFrames()).thenReturn(10L);
        when(summary.getWins()).thenReturn(6L);
        when(summary.getLosses()).thenReturn(4L);

        FrameRepository.GlobalPlayerRecentFormProjection recent = org.mockito.Mockito.mock(
                FrameRepository.GlobalPlayerRecentFormProjection.class);
        when(recent.getUserId()).thenReturn(14);
        when(recent.getOutcome()).thenReturn("W");
        when(frameRepository.findGlobalPlayerPerformance()).thenReturn(List.of(summary));
        when(frameRepository.findGlobalPlayerRecentForm()).thenReturn(List.of(recent));

        PlayerPerformanceService.Snapshot snapshot = service.exposeCalculateSnapshot();

        assertEquals(1, snapshot.stats().size());
        assertEquals(10, snapshot.stats().get(0).totalFrames());
        assertEquals(6, snapshot.stats().get(0).wins());
        assertEquals(List.of("W"), snapshot.stats().get(0).recentForm());
        verify(frameRepository).findGlobalPlayerPerformance();
        verify(frameRepository).findGlobalPlayerRecentForm();
    }

    @Test
    void usesAuthenticatedEmailToResolveCurrentPlayerAndFallsBackToDatabase() {
        User user = new User();
        user.setId(14);
        user.setName("Pragyesh");
        user.setEmail("pragyesh@example.com");
        when(userRepository.findByEmail("pragyesh@example.com")).thenReturn(Optional.of(user));
        when(cache.read()).thenReturn(Optional.of(new PlayerPerformanceService.Snapshot(
                java.time.LocalDateTime.now(), List.of())));

        PlayerPerformanceResponseDto response = service.getForAuthenticatedUser("PRAGYESH@EXAMPLE.COM");

        assertEquals("Pragyesh", response.player().displayName());
        assertEquals(0, response.player().totalFrames());
        assertEquals(new BigDecimal("0.0"), response.player().winRate());
        verify(userRepository).findByEmail("pragyesh@example.com");
    }

    @Test
    void refreshSnapshotRecalculatesEvenWhenCacheAlreadyHasSnapshot() {
        when(cache.write(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(frameRepository.findGlobalPlayerPerformance()).thenReturn(List.of());
        when(frameRepository.findGlobalPlayerRecentForm()).thenReturn(List.of());

        service.exposeRefreshSnapshot();

        verify(frameRepository).findGlobalPlayerPerformance();
        verify(frameRepository).findGlobalPlayerRecentForm();
        verify(cache).write(org.mockito.ArgumentMatchers.any(PlayerPerformanceService.Snapshot.class));
    }

    private PlayerPerformanceService.Stat stat(Integer id, String name, long wins, long losses, long total) {
        return new PlayerPerformanceService.Stat(id, name, null, total, wins, losses, List.of());
    }

    private static final class TestPlayerPerformanceService extends PlayerPerformanceService {
        private TestPlayerPerformanceService(FrameRepository frameRepository, UserRepository userRepository,
                                              PlayerPerformanceCache cache) {
            super(frameRepository, userRepository, cache);
        }

        private BigDecimal exposeCalculateWinRate(long wins, long totalFrames) {
            return calculateWinRate(wins, totalFrames);
        }

        private List<PlayerPerformanceResponseDto.CompetitorDto> exposeBuildCompetitorWindow(
                List<Stat> stats, Stat current) {
            return buildCompetitorWindow(stats, current);
        }

        private Snapshot exposeCalculateSnapshot() {
            return calculateSnapshot();
        }

        private Snapshot exposeRefreshSnapshot() {
            return refreshSnapshot();
        }
    }
}
