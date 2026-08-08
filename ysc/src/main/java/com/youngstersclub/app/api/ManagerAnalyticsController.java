package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.TodayEarningsResponseDto;
import com.youngstersclub.app.service.AnalyticsService;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager")
public class ManagerAnalyticsController {

    private final AnalyticsService analyticsService;

    public ManagerAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/earnings")
    public ResponseEntity<TodayEarningsResponseDto> getManagerEarnings(
            @RequestParam(required = false) LocalDate date,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(analyticsService.getEarningsForDate(date, actorEmail));
    }
}
