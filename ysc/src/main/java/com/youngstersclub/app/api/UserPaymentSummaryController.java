package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.service.UserPaymentSummaryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserPaymentSummaryController {

    private final UserPaymentSummaryService userPaymentSummaryService;

    public UserPaymentSummaryController(UserPaymentSummaryService userPaymentSummaryService) {
        this.userPaymentSummaryService = userPaymentSummaryService;
    }

    @GetMapping("/payment-summary")
    public ResponseEntity<UserPaymentSummaryDto> getPaymentSummary(@RequestParam Integer userId) {
        return ResponseEntity.ok(userPaymentSummaryService.getPaymentSummary(userId));
    }
}
