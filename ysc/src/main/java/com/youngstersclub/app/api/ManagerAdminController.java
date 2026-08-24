package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.BranchAccessUpdateRequest;
import com.youngstersclub.app.dto.ManagerAdminDto;
import com.youngstersclub.app.dto.ManagerBranchAccessDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.dto.PromoteManagerRequest;
import com.youngstersclub.app.service.ManagerAdminService;
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
@RequestMapping("/api/managers")
public class ManagerAdminController {

    private final ManagerAdminService managerAdminService;

    public ManagerAdminController(ManagerAdminService managerAdminService) {
        this.managerAdminService = managerAdminService;
    }

    @GetMapping("/current-branch")
    public ResponseEntity<?> getCurrentBranchManagers(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            List<ManagerAdminDto> managers = managerAdminService.getCurrentBranchManagers(actorEmail);
            return ResponseEntity.ok(managers);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/promote")
    public ResponseEntity<?> promoteManager(
            @RequestBody PromoteManagerRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(managerAdminService.promoteManager(request, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/{organizationUserId}/demote")
    public ResponseEntity<?> demoteManager(
            @PathVariable Long organizationUserId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            return ResponseEntity.ok(managerAdminService.demoteManager(organizationUserId, actorEmail));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping("/{organizationUserId}/deactivate")
    public ResponseEntity<?> deactivateManager(
            @PathVariable Long organizationUserId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            managerAdminService.deactivateManager(organizationUserId, actorEmail);
            return ResponseEntity.ok(new MessageResponseDto("Manager deactivated successfully"));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PutMapping("/{organizationUserId}/branch-access")
    public ResponseEntity<?> setStaffBranchAccess(
            @PathVariable Long organizationUserId,
            @RequestBody BranchAccessUpdateRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            ManagerBranchAccessDto access =
                managerAdminService.setStaffBranchAccess(organizationUserId, request, actorEmail);
            return ResponseEntity.ok(access);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }
}
