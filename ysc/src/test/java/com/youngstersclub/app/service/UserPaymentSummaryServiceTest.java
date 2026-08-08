package com.youngstersclub.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.PendingDueBreakdownDto;
import com.youngstersclub.app.dto.PendingFrameBreakdownDto;
import com.youngstersclub.app.dto.PendingKidsPlayBreakdownDto;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPaymentSummaryServiceTest {

    @Mock private PendingDueService pendingDueService;
    @Mock private FrameService frameService;
    @Mock private ConsumableService consumableService;
    @Mock private KidsPlayService kidsPlayService;

    @InjectMocks private UserPaymentSummaryService userPaymentSummaryService;

    @Test
    void buildDateDueBundleCalculatesSummaryFromSingleSetOfLists() {
        UserPaymentSummaryService.DateDueBundle bundle = userPaymentSummaryService.buildDateDueBundle(
                List.of(new PendingFrameBreakdownDto(1, "A vs B", LocalDateTime.of(2026, 8, 6, 10, 0), BigDecimal.valueOf(100))),
                List.of(new ConsumableDueRowDto(2L, "Tea", 2, BigDecimal.valueOf(10), BigDecimal.valueOf(20), LocalDateTime.of(2026, 8, 6, 11, 0))),
                List.of(new PendingKidsPlayBreakdownDto(3L, "Kid", LocalDateTime.of(2026, 8, 6, 12, 0), BigDecimal.valueOf(30))));

        assertEquals(new BigDecimal("100"), bundle.summary().getFrameDue());
        assertEquals(new BigDecimal("20"), bundle.summary().getConsumableDue());
        assertEquals(new BigDecimal("30"), bundle.summary().getKidsDue());
        assertEquals(new BigDecimal("150"), bundle.summary().getTotalDue());
        assertEquals(1, bundle.frames().size());
        assertEquals(1, bundle.consumables().size());
        assertEquals(1, bundle.kidsPlay().size());
    }

    @Test
    void getPendingDueBreakdownByDateReusesSingleBundle() {
        Integer userId = 25;
        LocalDate selectedDate = LocalDate.of(2026, 8, 6);
        when(frameService.getUserDueFramesByDate(userId, selectedDate))
                .thenReturn(List.of(new PendingFrameBreakdownDto(1, "A vs B", LocalDateTime.of(2026, 8, 6, 10, 0), BigDecimal.valueOf(50))));
        when(consumableService.getDueConsumablesByDate(userId, selectedDate))
                .thenReturn(List.of(new ConsumableDueRowDto(2L, "Tea", 1, BigDecimal.valueOf(15), BigDecimal.valueOf(15), LocalDateTime.of(2026, 8, 6, 11, 0))));
        when(kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate))
                .thenReturn(List.of(new PendingKidsPlayBreakdownDto(3L, "Kid", LocalDateTime.of(2026, 8, 6, 12, 0), BigDecimal.valueOf(20))));

        PendingDueBreakdownDto breakdown = userPaymentSummaryService.getPendingDueBreakdownByDate(userId, selectedDate);

        assertEquals(new BigDecimal("50"), breakdown.getFrameDue());
        assertEquals(new BigDecimal("15"), breakdown.getConsumableDue());
        assertEquals(new BigDecimal("20"), breakdown.getKidsDue());
        assertEquals(new BigDecimal("85"), breakdown.getTotalDue());
        verify(frameService).getUserDueFramesByDate(userId, selectedDate);
        verify(consumableService).getDueConsumablesByDate(userId, selectedDate);
        verify(kidsPlayService).getKidsDueBreakdownByDate(userId, selectedDate);
    }

    @Test
    void getBranchPaymentSummaryByDateUsesBranchSpecificBundle() {
        Integer userId = 25;
        Long branchId = 2L;
        LocalDate selectedDate = LocalDate.of(2026, 8, 6);
        when(frameService.getUserDueFramesByDate(userId, selectedDate, branchId))
                .thenReturn(List.of(new PendingFrameBreakdownDto(1, "A vs B", LocalDateTime.of(2026, 8, 6, 10, 0), BigDecimal.valueOf(70))));
        when(consumableService.getDueConsumablesByDate(userId, selectedDate, branchId))
                .thenReturn(List.of());
        when(kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate, branchId))
                .thenReturn(List.of(new PendingKidsPlayBreakdownDto(3L, "Kid", LocalDateTime.of(2026, 8, 6, 12, 0), BigDecimal.valueOf(10))));

        UserPaymentSummaryDto summary = userPaymentSummaryService.getBranchPaymentSummaryByDate(userId, selectedDate, branchId);

        assertEquals(new BigDecimal("70"), summary.getFrameDue());
        assertEquals(BigDecimal.ZERO, summary.getConsumableDue());
        assertEquals(new BigDecimal("10"), summary.getKidsDue());
        assertEquals(new BigDecimal("80"), summary.getTotalDue());
    }
}
