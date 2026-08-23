package com.youngstersclub.app.api;

import com.youngstersclub.app.dto.BranchExpenseCreateRequest;
import com.youngstersclub.app.dto.BranchExpenseDto;
import com.youngstersclub.app.dto.ExpensePayerOptionDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.service.BranchExpenseService;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/expenses")
public class BranchExpenseController {

    private final BranchExpenseService branchExpenseService;

    public BranchExpenseController(BranchExpenseService branchExpenseService) {
        this.branchExpenseService = branchExpenseService;
    }

    @GetMapping
    public ResponseEntity<?> getExpenses(
            @RequestParam int year,
            @RequestParam int month,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            List<BranchExpenseDto> response = branchExpenseService.getCurrentBranchExpenses(year, month, actorEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @GetMapping("/eligible-payers")
    public ResponseEntity<?> getEligiblePayers(
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            List<ExpensePayerOptionDto> response = branchExpenseService.getEligiblePayers(actorEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createExpense(
            @RequestBody BranchExpenseCreateRequest request,
            @RequestHeader(name = "X-User-Email", required = false) String actorEmail) {
        try {
            BranchExpenseDto response = branchExpenseService.createExpense(request, actorEmail);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponseDto(ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponseDto(ex.getMessage()));
        }
    }
}
