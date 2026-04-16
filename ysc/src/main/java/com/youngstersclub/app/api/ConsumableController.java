package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.ConsumableHistoryRowDto;
import com.youngstersclub.app.dto.ConsumableItemOptionDto;
import com.youngstersclub.app.dto.ConsumableOrderCreateRequest;
import com.youngstersclub.app.dto.ConsumableOrderResponseDto;
import com.youngstersclub.app.service.ConsumableService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consumables")
public class ConsumableController {

    private final ConsumableService consumableService;

    public ConsumableController(ConsumableService consumableService) {
        this.consumableService = consumableService;
    }

    @GetMapping("/items/search")
    public ResponseEntity<List<ConsumableItemOptionDto>> searchItems(@RequestParam String query) {
        return ResponseEntity.ok(consumableService.searchActiveItems(query));
    }

    @PostMapping("/order")
    public ResponseEntity<ConsumableOrderResponseDto> createOrder(@RequestBody ConsumableOrderCreateRequest request) {
        return ResponseEntity.ok(consumableService.createOrder(request));
    }

    @GetMapping("/orders/due")
    public ResponseEntity<List<ConsumableDueRowDto>> getDueOrders(@RequestParam Integer userId) {
        return ResponseEntity.ok(consumableService.getDueConsumables(userId));
    }

    @GetMapping("/my-history")
    public ResponseEntity<List<ConsumableHistoryRowDto>> getMyHistory(@RequestParam Integer userId) {
        return ResponseEntity.ok(consumableService.getConsumableHistory(userId));
    }
}
