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

        return buildDateDueBundle(userId, selectedDate).summary();
    }

    public UserPaymentSummaryDto getBranchPaymentSummaryByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        return buildBranchDateDueBundle(userId, selectedDate, branchId).summary();
    }

    public Map<Integer, UserPaymentSummaryDto> getPaymentSummaries(List<Integer> userIds) {
        return pendingDueService.getPaymentSummaries(userIds);
    }

    public PendingDueBreakdownDto getPendingDueBreakdownByDate(Integer userId, LocalDate selectedDate) {
        DateDueBundle bundle = buildDateDueBundle(userId, selectedDate);

        return new PendingDueBreakdownDto(
                bundle.frames(),
                bundle.consumables(),
                bundle.kidsPlay(),
                bundle.summary().getFrameDue(),
                bundle.summary().getConsumableDue(),
                bundle.summary().getKidsDue(),
                bundle.summary().getTotalDue());
    }

    public PendingDueBreakdownDto getBranchPendingDueBreakdownByDate(Integer userId, LocalDate selectedDate, Long branchId) {
        DateDueBundle bundle = buildBranchDateDueBundle(userId, selectedDate, branchId);

        return new PendingDueBreakdownDto(
                bundle.frames(),
                bundle.consumables(),
                bundle.kidsPlay(),
                bundle.summary().getFrameDue(),
                bundle.summary().getConsumableDue(),
                bundle.summary().getKidsDue(),
                bundle.summary().getTotalDue());
    }

    protected DateDueBundle buildDateDueBundle(Integer userId, LocalDate selectedDate) {
        if (userId == null || selectedDate == null) {
            return emptyDateDueBundle();
        }

        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate);
        return buildDateDueBundle(frames, consumables, kidsPlay);
    }

    protected DateDueBundle buildBranchDateDueBundle(Integer userId, LocalDate selectedDate, Long branchId) {
        if (userId == null || selectedDate == null || branchId == null) {
            return emptyDateDueBundle();
        }

        List<PendingFrameBreakdownDto> frames = frameService.getUserDueFramesByDate(userId, selectedDate, branchId);
        List<ConsumableDueRowDto> consumables = consumableService.getDueConsumablesByDate(userId, selectedDate, branchId);
        List<PendingKidsPlayBreakdownDto> kidsPlay = kidsPlayService.getKidsDueBreakdownByDate(userId, selectedDate, branchId);
        return buildDateDueBundle(frames, consumables, kidsPlay);
    }

    protected DateDueBundle buildDateDueBundle(
            List<PendingFrameBreakdownDto> frames,
            List<ConsumableDueRowDto> consumables,
            List<PendingKidsPlayBreakdownDto> kidsPlay) {
        List<PendingFrameBreakdownDto> safeFrames = frames == null ? List.of() : frames;
        List<ConsumableDueRowDto> safeConsumables = consumables == null ? List.of() : consumables;
        List<PendingKidsPlayBreakdownDto> safeKidsPlay = kidsPlay == null ? List.of() : kidsPlay;

        BigDecimal frameDue = safeFrames.stream()
                .map(PendingFrameBreakdownDto::getDueAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal consumableDue = safeConsumables.stream()
                .map(ConsumableDueRowDto::getTotalCost)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kidsDue = safeKidsPlay.stream()
                .map(PendingKidsPlayBreakdownDto::getAmount)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DateDueBundle(
                safeFrames,
                safeConsumables,
                safeKidsPlay,
                new UserPaymentSummaryDto(frameDue, consumableDue, kidsDue));
    }

    protected DateDueBundle emptyDateDueBundle() {
        return new DateDueBundle(List.of(), List.of(), List.of(), new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    protected record DateDueBundle(
            List<PendingFrameBreakdownDto> frames,
            List<ConsumableDueRowDto> consumables,
            List<PendingKidsPlayBreakdownDto> kidsPlay,
            UserPaymentSummaryDto summary) {
    }
}
