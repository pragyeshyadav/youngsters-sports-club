package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.StartFrameRequest;
import com.youngstersclub.app.service.FrameService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.youngstersclub.app.util.TimeUtil;

@RestController
@RequestMapping("/api/frame")
public class FrameController {

    private final FrameService frameService;

    public FrameController(FrameService frameService) {
        this.frameService = frameService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startFrame(
            @RequestBody StartFrameRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.startFrame(request, actorEmail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(e.getMessage()));
        }
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
    public ResponseEntity<?> getTodayOngoingFrames(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.getTodayOngoingFrames(actorEmail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @GetMapping("/completed/today")
    public ResponseEntity<?> getTodayCompletedFrames(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.getTodayCompletedFrames(actorEmail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @GetMapping("/completed")
    public ResponseEntity<?> getCompletedFramesByDate(
            @RequestParam(required = false) LocalDate date,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        LocalDate today = TimeUtil.nowIST().toLocalDate();
        LocalDate targetDate = date == null ? today : date;

        if (targetDate.isAfter(today)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Future dates are not allowed"));
        }

        if (targetDate.isBefore(today.minusDays(60))) {
            return ResponseEntity.badRequest().body(Map.of("message", "You can only view completed frames for the last 60 days"));
        }

        try {
            return ResponseEntity.ok(frameService.getCompletedFramesByDate(targetDate, actorEmail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @GetMapping("/user-due")
    public ResponseEntity<List<Map<String, Object>>> getUserDueFrames(@RequestParam Integer userId) {
        return ResponseEntity.ok(frameService.getUserDueFrames(userId));
    }

    @GetMapping("/user-due/current-branch")
    public ResponseEntity<List<Map<String, Object>>> getCurrentBranchUserDueFrames(
            @RequestParam Integer userId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(frameService.getUserDueFrames(userId, actorEmail));
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
    public ResponseEntity<?> getFramePlayers(
            @PathVariable Integer frameId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.getFramePlayers(frameId, actorEmail));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @GetMapping("/{frameId}")
    public ResponseEntity<?> getFrameDetails(
            @PathVariable Integer frameId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.getFrameDetails(frameId, actorEmail));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @PostMapping("/end/{frameId}")
    public ResponseEntity<?> endFrame(
            @PathVariable Integer frameId,
            @RequestBody com.youngstersclub.app.dto.EndFrameTeamRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(frameService.endFrame(frameId, request, actorEmail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponseDto(e.getMessage()));
        }
    }

    @PostMapping("/reject/{frameId}")
    public ResponseEntity<Void> rejectFrame(@PathVariable Integer frameId) {
        frameService.rejectFrame(frameId);
        return ResponseEntity.ok().build();
    }
}
