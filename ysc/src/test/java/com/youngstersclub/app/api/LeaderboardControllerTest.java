package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.service.FrameService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

  @Mock private FrameService frameService;

  @InjectMocks private LeaderboardController leaderboardController;

  @Test
  void getTopPlayersDelegatesToBranchAwareService() {
    List<Map<String, Object>> expected = List.of(Map.of("name", "Winner", "wins", 5L));
    when(frameService.getTopPlayers("manager@test.com", 2026, 7)).thenReturn(expected);

    List<Map<String, Object>> response =
        leaderboardController.getTopPlayers("manager@test.com", 2026, 7);

    assertSame(expected, response);
    verify(frameService).getTopPlayers("manager@test.com", 2026, 7);
  }

  @Test
  void getTopPlayersMapsInvalidMonthToBadRequest() {
    when(frameService.getTopPlayers("manager@test.com", 2026, 13))
        .thenThrow(new IllegalArgumentException("Month must be between 1 and 12"));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> leaderboardController.getTopPlayers("manager@test.com", 2026, 13));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertEquals("Month must be between 1 and 12", exception.getReason());
  }

  @Test
  void getTopPlayersMapsSecurityFailureToForbidden() {
    when(frameService.getTopPlayers(null, null, null))
        .thenThrow(new SecurityException("Authenticated user email is required"));

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> leaderboardController.getTopPlayers(null, null, null));

    assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    assertEquals("Authenticated user email is required", exception.getReason());
  }
}
