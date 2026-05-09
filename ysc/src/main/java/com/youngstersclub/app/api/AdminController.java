package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.dto.ConsumableStockCreateRequest;
import com.youngstersclub.app.dto.ConsumableStockCreateResponseDto;
import com.youngstersclub.app.dto.ConsumableStockReportRowDto;
import com.youngstersclub.app.service.ConsumableService;
import com.youngstersclub.app.service.AdminAnalyticsService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAnalyticsService adminAnalyticsService;
    private final ConsumableService consumableService;

    public AdminController(AdminAnalyticsService adminAnalyticsService, ConsumableService consumableService) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.consumableService = consumableService;
    }

    @GetMapping("/monthly-earnings")
    public ResponseEntity<AdminMonthlyEarningsDto> getMonthlyEarnings(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(adminAnalyticsService.getMonthlyEarnings(month, year));
    }

    @PostMapping("/consumables/stock")
    public ResponseEntity<ConsumableStockCreateResponseDto> addConsumableStock(
            @RequestBody ConsumableStockCreateRequest request) {
        return ResponseEntity.ok(consumableService.addStock(request));
    }

    @GetMapping("/consumables/stock-report")
    public ResponseEntity<List<ConsumableStockReportRowDto>> getConsumableStockReport(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(consumableService.getStockReport(month, year));
    }
}
