package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.GameActivityOptionDto;
import com.youngstersclub.app.dto.GameActivityOrderCreateRequest;
import com.youngstersclub.app.dto.GameActivityOrderResponseDto;
import com.youngstersclub.app.service.GameActivityService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/play-zone-activities")
public class GameActivityController {

    private final GameActivityService gameActivityService;

    public GameActivityController(GameActivityService gameActivityService) {
        this.gameActivityService = gameActivityService;
    }

    @GetMapping("/games/search")
    public ResponseEntity<List<GameActivityOptionDto>> searchGames(@RequestParam String query) {
        return ResponseEntity.ok(gameActivityService.searchActiveGames(query));
    }

    @PostMapping("/order")
    public ResponseEntity<GameActivityOrderResponseDto> createOrders(@RequestBody GameActivityOrderCreateRequest request) {
        return ResponseEntity.ok(gameActivityService.createOrders(request));
    }
}
