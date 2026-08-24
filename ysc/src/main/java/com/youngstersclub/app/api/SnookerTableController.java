package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.ActiveStateRequest;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.SnookerTableAdminRequest;
import com.youngstersclub.app.dto.SnookerTableResponseDto;
import com.youngstersclub.app.dto.SnookerTableStatusDto;
import com.youngstersclub.app.service.SnookerTableService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/tables/manage")
    public ResponseEntity<?> getTablesForAdmin(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(snookerTableService.getCurrentBranchTablesForAdmin(actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/tables")
    public ResponseEntity<?> createTable(
            @RequestBody SnookerTableAdminRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(snookerTableService.createTable(request, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PutMapping("/tables/{tableId}")
    public ResponseEntity<?> updateTable(
            @PathVariable Long tableId,
            @RequestBody SnookerTableAdminRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(snookerTableService.updateTable(tableId, request, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PutMapping("/tables/{tableId}/active")
    public ResponseEntity<?> setTableActive(
            @PathVariable Long tableId,
            @RequestBody ActiveStateRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            boolean isActive = request != null && Boolean.TRUE.equals(request.getIsActive());
            return ResponseEntity.ok(snookerTableService.setTableActive(tableId, isActive, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/tables/{tableId}/release")
    public ResponseEntity<?> releaseTable(
            @PathVariable Long tableId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(snookerTableService.releaseTable(tableId, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }
}
