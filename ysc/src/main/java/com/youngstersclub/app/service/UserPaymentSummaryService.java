package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.PendingDueBreakdownDto;
import com.youngstersclub.app.dto.PendingFrameBreakdownDto;
import com.youngstersclub.app.dto.PendingKidsPlayBreakdownDto;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserPaymentSummaryService {

    private final PendingDueService pendingDueService;
    private final FrameService frameService;
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;

    public UserPaymentSummaryService(
            PendingDueService pendingDueService,
            FrameService frameService,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService) {
        this.pendingDueService = pendingDueService;
        this.frameService = frameService;
        this.consumableService = consumableService;
        this.kidsPlayService = kidsPlayService;
    }

    public UserPaymentSummaryDto getPaymentSummary(Integer userId) {
        return pendingDueService.getPaymentSummary(userId);
    }

    public UserPaymentSummaryDto getBranchPaymentSummary(Integer userId, Long branchId) {
        return pendingDueService.getBranchPaymentSummary(userId, branchId);
    }

    public UserPaymentSummaryDto getPaymentSummaryByDate(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate);

        BigDecimal frameDue = frames.stream()
                .map(PendingFrameBreakdownDto::getDueAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumableDue = consumables.stream()
                .map(ConsumableDueRowDto::getTotalCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kidsDue = kidsPlay.stream()
                .map(PendingKidsPlayBreakdownDto::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UserPaymentSummaryDto(frameDue, consumableDue, kidsDue);
    }

    public UserPaymentSummaryDto getBranchPaymentSummaryByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate, branchId);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate, branchId);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate, branchId);

        BigDecimal frameDue = frames.stream()
                .map(PendingFrameBreakdownDto::getDueAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumableDue = consumables.stream()
                .map(ConsumableDueRowDto::getTotalCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kidsDue = kidsPlay.stream()
                .map(PendingKidsPlayBreakdownDto::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UserPaymentSummaryDto(frameDue, consumableDue, kidsDue);
    }

    public Map<Integer, UserPaymentSummaryDto> getPaymentSummaries(List<Integer> userIds) {
        return pendingDueService.getPaymentSummaries(userIds);
    }

    public PendingDueBreakdownDto getPendingDueBreakdownByDate(Integer userId, LocalDate selectedDate) {
        UserPaymentSummaryDto summary = getPaymentSummaryByDate(userId, selectedDate);
        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate);

        return new PendingDueBreakdownDto(
                frames,
                consumables,
                kidsPlay,
                summary.getFrameDue(),
                summary.getConsumableDue(),
                summary.getKidsDue(),
                summary.getTotalDue());
    }

    public PendingDueBreakdownDto getBranchPendingDueBreakdownByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        UserPaymentSummaryDto summary = getBranchPaymentSummaryByDate(userId, selectedDate, branchId);
        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate, branchId);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate, branchId);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate, branchId);

        return new PendingDueBreakdownDto(
                frames,
                consumables,
                kidsPlay,
                summary.getFrameDue(),
                summary.getConsumableDue(),
                summary.getKidsDue(),
                summary.getTotalDue());
    }
}
