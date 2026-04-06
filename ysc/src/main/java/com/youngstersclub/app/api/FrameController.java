package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.service.FrameService;
import java.math.BigDecimal;
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

    @GetMapping("/user-ongoing")
    public ResponseEntity<?> getUserOngoingFrame(@RequestParam Integer userId) {
        return ResponseEntity.ok(frameService.getUserOngoingFrame(userId));
    }

    @GetMapping("/ongoing/today")
    public ResponseEntity<List<Map<String, Object>>> getTodayOngoingFrames() {
        return ResponseEntity.ok(frameService.getTodayOngoingFrames());
    }

    @GetMapping("/completed/today")
    public ResponseEntity<List<Map<String, Object>>> getTodayCompletedFrames() {
        return ResponseEntity.ok(frameService.getTodayCompletedFrames());
    }

    @GetMapping("/user-due")
    public ResponseEntity<List<Map<String, Object>>> getUserDueFrames(@RequestParam Integer userId) {
        return ResponseEntity.ok(frameService.getUserDueFrames(userId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> getUserFrameHistory(@RequestParam Integer userId) {
        return ResponseEntity.ok(frameService.getUserFrameHistory(userId));
    }

    @GetMapping("/total-due")
    public ResponseEntity<BigDecimal> getTotalDue(@RequestParam Integer userId) {
        return ResponseEntity.ok(frameService.getTotalDue(userId));
    }

    @GetMapping("/{frameId}/players")
    public ResponseEntity<List<Map<String, Object>>> getFramePlayers(@PathVariable Integer frameId) {
        return ResponseEntity.ok(frameService.getFramePlayers(frameId));
    }

    @GetMapping("/{frameId}")
    public ResponseEntity<?> getFrameDetails(@PathVariable Integer frameId) {
        return ResponseEntity.ok(frameService.getFrameDetails(frameId));
    }

    @PostMapping("/end/{frameId}")
    public ResponseEntity<?> endFrame(@PathVariable Integer frameId, @RequestBody Map<String, Integer> request) {
        return ResponseEntity.ok(frameService.endFrame(frameId, request.get("winnerId"), request.get("looserId")));
    }

    @PostMapping("/reject/{frameId}")
    public ResponseEntity<Void> rejectFrame(@PathVariable Integer frameId) {
        frameService.rejectFrame(frameId);
        return ResponseEntity.ok().build();
    }
}
