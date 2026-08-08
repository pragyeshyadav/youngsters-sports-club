package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.SnookerTableResponseDto;
import com.youngstersclub.app.dto.SnookerTableStatusDto;
import com.youngstersclub.app.service.SnookerTableService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/snooker")
public class SnookerTableController {

    private final SnookerTableService snookerTableService;

    public SnookerTableController(SnookerTableService snookerTableService) {
        this.snookerTableService = snookerTableService;
    }

    @GetMapping("/tables")
    public ResponseEntity<List<SnookerTableResponseDto>> getAvailableTables(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(snookerTableService.getCurrentBranchAvailableTables(actorEmail));
    }

    @GetMapping("/tables/status")
    public ResponseEntity<List<SnookerTableStatusDto>> getTableStatuses(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(snookerTableService.getCurrentBranchTableStatuses(actorEmail));
    }
}
