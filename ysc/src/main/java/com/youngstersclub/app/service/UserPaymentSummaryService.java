package com.youngstersclub.app.service;

import com.youngstersclub.app.dto.ConsumableDueRowDto;
import com.youngstersclub.app.dto.PendingDueBreakdownDto;
import com.youngstersclub.app.dto.PendingFrameBreakdownDto;
import com.youngstersclub.app.dto.PendingKidsPlayBreakdownDto;
import com.youngstersclub.app.dto.UserPaymentSummaryDto;
import com.youngstersclub.app.repository.FrameRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserPaymentSummaryService {

    private final FrameRepository frameRepository;
    private final FrameService frameService;
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;

    public UserPaymentSummaryService(
            FrameRepository frameRepository,
            FrameService frameService,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService) {
        this.frameRepository = frameRepository;
        this.frameService = frameService;
        this.consumableService = consumableService;
        this.kidsPlayService = kidsPlayService;
    }

    public UserPaymentSummaryDto getPaymentSummary(Integer userId) {
        if (userId == null) {
            return new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return getPaymentSummaries(List.of(userId)).getOrDefault(
                userId,
                new UserPaymentSummaryDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
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

    public Map<Integer, UserPaymentSummaryDto> getPaymentSummaries(List<Integer> userIds) {
        Map<Integer, UserPaymentSummaryDto> summaries = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return summaries;
        }

        Map<Integer, BigDecimal> frameDueByUserId = frameRepository.getTotalDueForUsers(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        FrameRepository.UserDueProjection::getUserId,
                        projection -> projection.getAmount() == null ? BigDecimal.ZERO : projection.getAmount()));
        Map<Integer, BigDecimal> consumableDueByUserId = consumableService.getConsumableDueMap(userIds);
        Map<Integer, BigDecimal> kidsDueByUserId = kidsPlayService.getKidsDueMap(userIds);

        for (Integer userId : userIds) {
            summaries.put(
                    userId,
                    new UserPaymentSummaryDto(
                            frameDueByUserId.getOrDefault(userId, BigDecimal.ZERO),
                            consumableDueByUserId.getOrDefault(userId, BigDecimal.ZERO),
                            kidsDueByUserId.getOrDefault(userId, BigDecimal.ZERO)));
        }

        return summaries;
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
}
