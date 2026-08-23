package com.youngstersclub.app.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.BranchExpenseCreateRequest;
import com.youngstersclub.app.dto.BranchExpenseDto;
import com.youngstersclub.app.dto.ExpensePayerOptionDto;
import com.youngstersclub.app.dto.MessageResponseDto;
import com.youngstersclub.app.enums.ExpenseType;
import com.youngstersclub.app.service.BranchExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class BranchExpenseControllerTest {

    @Mock
    private BranchExpenseService branchExpenseService;

    @InjectMocks
    private BranchExpenseController branchExpenseController;

    @Test
    void getExpensesReturnsBranchScopedPayload() {
        List<BranchExpenseDto> expected = List.of(new BranchExpenseDto(
                11L,
                "Electricity Bill",
                new BigDecimal("6500.00"),
                ExpenseType.COUNTER_CASH,
                LocalDate.of(2026, 8, 8),
                "July electricity bill",
                12,
                "Prince",
                2,
                "Pragyesh",
                20L,
                "Satna",
                LocalDateTime.of(2026, 8, 8, 10, 30)));
        when(branchExpenseService.getCurrentBranchExpenses(2026, 8, "admin@test.com"))
                .thenReturn(expected);

        ResponseEntity<?> response = branchExpenseController.getExpenses(2026, 8, "admin@test.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(branchExpenseService).getCurrentBranchExpenses(2026, 8, "admin@test.com");
    }

    @Test
    void getEligiblePayersReturnsForbiddenResponseForSecurityErrors() {
        when(branchExpenseService.getEligiblePayers("admin@test.com"))
                .thenThrow(new SecurityException("Current branch is required"));

        ResponseEntity<?> response = branchExpenseController.getEligiblePayers("admin@test.com");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Current branch is required", ((MessageResponseDto) response.getBody()).getMessage());
    }

    @Test
    void createExpenseReturnsBadRequestForValidationFailures() {
        BranchExpenseCreateRequest request = new BranchExpenseCreateRequest();
        request.setExpenseName("Electricity Bill");
        request.setAmount(new BigDecimal("0"));
        request.setExpenseType(ExpenseType.COUNTER_CASH);
        request.setPaidByUserId(12);
        request.setExpenseDate(LocalDate.of(2026, 8, 8));
        when(branchExpenseService.createExpense(request, "admin@test.com"))
                .thenThrow(new IllegalArgumentException("Expense amount must be greater than zero"));

        ResponseEntity<?> response = branchExpenseController.createExpense(request, "admin@test.com");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Expense amount must be greater than zero", ((MessageResponseDto) response.getBody()).getMessage());
    }

    @Test
    void createExpenseDelegatesToServiceForSuccessfulSave() {
        BranchExpenseCreateRequest request = new BranchExpenseCreateRequest();
        request.setExpenseName("Electricity Bill");
        request.setAmount(new BigDecimal("6500"));
        request.setExpenseType(ExpenseType.COUNTER_CASH);
        request.setPaidByUserId(12);
        request.setExpenseDate(LocalDate.of(2026, 8, 8));
        request.setNotes("July electricity bill");

        BranchExpenseDto expected = new BranchExpenseDto(
                11L,
                "Electricity Bill",
                new BigDecimal("6500.00"),
                ExpenseType.COUNTER_CASH,
                LocalDate.of(2026, 8, 8),
                "July electricity bill",
                12,
                "Prince",
                2,
                "Pragyesh",
                20L,
                "Satna",
                LocalDateTime.of(2026, 8, 8, 10, 30));
        when(branchExpenseService.createExpense(request, "admin@test.com"))
                .thenReturn(expected);

        ResponseEntity<?> response = branchExpenseController.createExpense(request, "admin@test.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(branchExpenseService).createExpense(request, "admin@test.com");
    }

    @Test
    void getEligiblePayersReturnsAvailableStaff() {
        List<ExpensePayerOptionDto> expected = List.of(
                new ExpensePayerOptionDto(2, "Pragyesh Yadav", "SUPER_ADMIN"),
                new ExpensePayerOptionDto(12, "Prince Singh", "MANAGER"));
        when(branchExpenseService.getEligiblePayers("admin@test.com")).thenReturn(expected);

        ResponseEntity<?> response = branchExpenseController.getEligiblePayers("admin@test.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(expected, response.getBody());
        verify(branchExpenseService).getEligiblePayers("admin@test.com");
    }
}
