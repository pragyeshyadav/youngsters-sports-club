package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.KidsSessionEndRequest;
import com.youngstersclub.app.dto.KidsSessionResponseDto;
import com.youngstersclub.app.dto.KidsSessionStartRequest;
import com.youngstersclub.app.service.KidsPlayService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kids-session")
public class KidsPlaySessionController {

    private final KidsPlayService kidsPlayService;

    public KidsPlaySessionController(KidsPlayService kidsPlayService) {
        this.kidsPlayService = kidsPlayService;
    }

    @PostMapping("/start")
    public ResponseEntity<KidsSessionResponseDto> start(
            @RequestBody KidsSessionStartRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(kidsPlayService.startSession(request, actorEmail));
    }

    @PostMapping("/end")
    public ResponseEntity<KidsSessionResponseDto> end(
            @RequestBody KidsSessionEndRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(kidsPlayService.endSession(request, actorEmail));
    }

    @PostMapping("/reject")
    public ResponseEntity<KidsSessionResponseDto> reject(
            @RequestBody KidsSessionEndRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(kidsPlayService.rejectSession(request.getSessionId(), actorEmail));
    }

    @GetMapping("/active")
    public ResponseEntity<List<KidsSessionResponseDto>> active(
            @RequestParam(required = false) Integer parentUserId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        if (parentUserId == null) {
            return ResponseEntity.ok(kidsPlayService.getAllActiveSessions(actorEmail));
        }
        return ResponseEntity.ok(kidsPlayService.getActiveSessions(parentUserId, actorEmail));
    }
}
