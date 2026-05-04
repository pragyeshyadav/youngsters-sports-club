package com.youngstersclub.app.api;

import com.youngstersclub.app.service.FrameService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final FrameService frameService;

    public LeaderboardController(FrameService frameService) {
        this.frameService = frameService;
    }

    @GetMapping("/top-players")
    public List<Map<String, Object>> getTopPlayers() {
        return frameService.getTopPlayers();
    }
}
