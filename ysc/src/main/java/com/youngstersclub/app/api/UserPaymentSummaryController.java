package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.PendingDueBreakdownDto;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.service.OrganizationContextService;
import com.youngstersclub.app.service.UserPaymentSummaryService;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserPaymentSummaryController {

    private final UserPaymentSummaryService userPaymentSummaryService;
    private final OrganizationContextService organizationContextService;

    public UserPaymentSummaryController(
            UserPaymentSummaryService userPaymentSummaryService,
            OrganizationContextService organizationContextService) {
        this.userPaymentSummaryService = userPaymentSummaryService;
        this.organizationContextService = organizationContextService;
    }

    @GetMapping("/payment-summary")
    public ResponseEntity<UserPaymentSummaryDto> getPaymentSummary(@RequestParam Integer userId) {
        return ResponseEntity.ok(userPaymentSummaryService.getPaymentSummary(userId));
    }

    @GetMapping("/payment-summary-by-date")
    public ResponseEntity<UserPaymentSummaryDto> getPaymentSummaryByDate(
            @RequestParam Integer userId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(userPaymentSummaryService.getPaymentSummaryByDate(userId, date));
    }

    @GetMapping("/payment-breakdown-by-date")
    public ResponseEntity<PendingDueBreakdownDto> getPaymentBreakdownByDate(
            @RequestParam Integer userId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(userPaymentSummaryService.getPendingDueBreakdownByDate(userId, date));
    }

    @GetMapping("/payment-summary/current-branch")
    public ResponseEntity<UserPaymentSummaryDto> getCurrentBranchPaymentSummary(
            @RequestParam Integer userId,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(userPaymentSummaryService.getBranchPaymentSummary(
                userId,
                resolveBranchId(actorEmail)));
    }

    @GetMapping("/payment-summary-by-date/current-branch")
    public ResponseEntity<UserPaymentSummaryDto> getCurrentBranchPaymentSummaryByDate(
            @RequestParam Integer userId,
            @RequestParam LocalDate date,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(userPaymentSummaryService.getBranchPaymentSummaryByDate(
                userId,
                date,
                resolveBranchId(actorEmail)));
    }

    @GetMapping("/payment-breakdown-by-date/current-branch")
    public ResponseEntity<PendingDueBreakdownDto> getCurrentBranchPaymentBreakdownByDate(
            @RequestParam Integer userId,
            @RequestParam LocalDate date,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        return ResponseEntity.ok(userPaymentSummaryService.getBranchPendingDueBreakdownByDate(
                userId,
                date,
                resolveBranchId(actorEmail)));
    }

    private Long resolveBranchId(String actorEmail) {
        String normalizedEmail = actorEmail == null ? "" : actorEmail.trim().toLowerCase();
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Actor email is required");
        }
        // Reuse branch-scoped summary service through the current organization/branch context.
        var context = organizationContextService.resolveContext(normalizedEmail);
        if (context.getCurrentBranch() == null) {
            throw new IllegalArgumentException("Current branch context is required");
        }
        return context.getCurrentBranch().getId();
    }
}
