package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.AdminMonthlyEarningsDto;
import com.youngstersclub.app.service.AdminAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @GetMapping("/monthly-earnings")
    public ResponseEntity<AdminMonthlyEarningsDto> getMonthlyEarnings(
            @RequestParam int month,
            @RequestParam int year) {
        return ResponseEntity.ok(adminAnalyticsService.getMonthlyEarnings(month, year));
    }
}
