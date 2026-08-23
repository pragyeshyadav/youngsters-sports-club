package com.youngstersclub.app.api;

import com.youngstersclub.app.service.FrameService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final FrameService frameService;

    public LeaderboardController(FrameService frameService) {
        this.frameService = frameService;
    }

    @GetMapping("/top-players")
    public List<Map<String, Object>> getTopPlayers(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail,
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month) {
        try {
            return frameService.getTopPlayers(actorEmail, year, month);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (SecurityException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }
}
