package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.CustomerBranchDue;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.repository.ConsumableOrderRepository;
import com.youngstersclub.app.repository.FrameRepository;
import com.youngstersclub.app.repository.GameActivityOrderRepository;
import com.youngstersclub.app.repository.KidsPlaySessionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PendingDueServiceTest {

    @Mock private FrameRepository frameRepository;
    @Mock private ConsumableService consumableService;
    @Mock private KidsPlayService kidsPlayService;
    @Mock private CustomerBranchDueCalculatorService customerBranchDueCalculatorService;

    @InjectMocks private PendingDueService pendingDueService;

    @Test
    void getBranchPaymentSummariesReturnsBranchScopedTotals() {
        List<Integer> userIds = List.of(10, 20);

        when(customerBranchDueCalculatorService.calculateCustomerDues(List.of(10L, 20L), 2L)).thenReturn(Map.of(
                10L, new CustomerBranchDue(10L, 2L, BigDecimal.valueOf(120), BigDecimal.valueOf(30), BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(165)),
                20L, new CustomerBranchDue(20L, 2L, BigDecimal.valueOf(50), BigDecimal.ZERO, BigDecimal.valueOf(20), BigDecimal.valueOf(5), BigDecimal.valueOf(75))));

        Map<Integer, UserPaymentSummaryDto> summaries = pendingDueService.getBranchPaymentSummaries(userIds, 2L);

        assertEquals(0, BigDecimal.valueOf(165).compareTo(summaries.get(10).getTotalDue()));
        assertEquals(0, BigDecimal.valueOf(75).compareTo(summaries.get(20).getTotalDue()));
    }

    @Test
    void getBranchPendingDueMapReturnsEmptyForMissingBranch() {
        Map<Integer, BigDecimal> dueMap = pendingDueService.getBranchPendingDueMap(List.of(10), null);

        assertTrue(dueMap.isEmpty());
    }

    @Test
    void calculateCustomerDueReturnsSeparateBranchScopedBuckets() {
        List<Integer> userIds = List.of(10);

        when(customerBranchDueCalculatorService.calculateCustomerDue(10L, 2L)).thenReturn(
                new CustomerBranchDue(
                        10L,
                        2L,
                        BigDecimal.valueOf(120),
                        BigDecimal.valueOf(30),
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(15),
                        BigDecimal.valueOf(175)));

        CustomerBranchDue due = pendingDueService.calculateCustomerDue(10L, 2L);

        assertEquals(10L, due.customerId());
        assertEquals(2L, due.branchId());
        assertEquals(0, BigDecimal.valueOf(120).compareTo(due.frameDue()));
        assertEquals(0, BigDecimal.valueOf(30).compareTo(due.consumableDue()));
        assertEquals(0, BigDecimal.valueOf(10).compareTo(due.kidsPlayDue()));
        assertEquals(0, BigDecimal.valueOf(15).compareTo(due.gameActivityDue()));
        assertEquals(0, BigDecimal.valueOf(175).compareTo(due.totalDue()));
    }
}
