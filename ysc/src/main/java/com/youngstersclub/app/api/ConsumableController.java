package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.ConsumableHistoryRowDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.ConsumableService;
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
@RequestMapping("/api/consumables")
public class ConsumableController {

    private final ConsumableService consumableService;
    private final OrganizationContextService organizationContextService;

    public ConsumableController(
            ConsumableService consumableService,
            OrganizationContextService organizationContextService) {
        this.consumableService = consumableService;
        this.organizationContextService = organizationContextService;
    }

    @GetMapping("/items/search")
    public ResponseEntity<List<ConsumableItemOptionDto>> searchItems(
            @RequestParam String query,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(consumableService.searchActiveItems(query, actorEmail));
    }

    @PostMapping("/order")
    public ResponseEntity<ConsumableOrderResponseDto> createOrder(
            @RequestBody ConsumableOrderCreateRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(consumableService.createOrder(request, actorEmail));
    }

    @GetMapping("/orders/due")
    public ResponseEntity<List<ConsumableDueRowDto>> getDueOrders(@RequestParam Integer userId) {
        return ResponseEntity.ok(consumableService.getDueConsumables(userId));
    }

    @GetMapping("/orders/due/current-branch")
    public ResponseEntity<List<ConsumableDueRowDto>> getCurrentBranchDueOrders(
            @RequestParam Integer userId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(consumableService.getDueConsumables(userId, resolveBranchId(actorEmail)));
    }

    @GetMapping("/my-history")
    public ResponseEntity<List<ConsumableHistoryRowDto>> getMyHistory(
            @RequestParam Integer userId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(consumableService.getConsumableHistory(userId, resolveBranchId(actorEmail)));
    }

    private Long resolveBranchId(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Actor email is required");
        }
        var context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current branch context is required");
        }
        return context.getCurrentBranch().getId();
    }
}
