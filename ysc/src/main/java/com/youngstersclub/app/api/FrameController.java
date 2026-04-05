package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.service.FrameService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/frame")
public class FrameController {

    private final FrameService frameService;

    public FrameController(FrameService frameService) {
        this.frameService = frameService;
    }

    @PostMapping("/start")
    public ResponseEntity<Integer> startFrame(@RequestBody StartFrameRequest request) {
        return ResponseEntity.ok(frameService.startFrame(request));
    }

    @GetMapping("/active")
    public ResponseEntity<?> getActiveFrame(@RequestParam Integer userId) {
        Map<String, Object> response = frameService.getActiveFrame(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{frameId}/players")
    public ResponseEntity<List<Map<String, Object>>> getFramePlayers(@PathVariable Integer frameId) {
        return ResponseEntity.ok(frameService.getFramePlayers(frameId));
    }

    @PostMapping("/end/{frameId}")
    public ResponseEntity<?> endFrame(@PathVariable Integer frameId, @RequestBody Map<String, Integer> request) {
        return ResponseEntity.ok(frameService.endFrame(frameId, request.get("winnerId"), request.get("looserId")));
    }
}
