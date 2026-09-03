package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.PlayerPerformanceResponseDto;
import com.youngstersclub.app.dto.UserSearchResultDto;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import com.youngstersclub.app.service.ManagerPlayerPerformanceService;
import com.youngstersclub.app.service.PlayerPerformanceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PlayerPerformanceController {
    private final PlayerPerformanceService playerPerformanceService;
    private final ManagerPlayerPerformanceService managerPlayerPerformanceService;

    public PlayerPerformanceController(
            PlayerPerformanceService playerPerformanceService,
            ManagerPlayerPerformanceService managerPlayerPerformanceService) {
        this.playerPerformanceService = playerPerformanceService;
        this.managerPlayerPerformanceService = managerPlayerPerformanceService;
    }

    @GetMapping("/api/player/performance")
    public PlayerPerformanceResponseDto getPerformance(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return playerPerformanceService.getForAuthenticatedUser(actorEmail);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/api/manager/player-performance/search")
    public List<UserSearchResultDto> searchPlayers(
            @RequestParam String query,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return managerPlayerPerformanceService.searchPlayers(actorEmail, query);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @GetMapping("/api/manager/player-performance/{playerId}")
    public PlayerPerformanceResponseDto getManagerPlayerPerformance(
            @PathVariable Integer playerId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return managerPlayerPerformanceService.getPlayerPerformance(actorEmail, playerId);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }
}
