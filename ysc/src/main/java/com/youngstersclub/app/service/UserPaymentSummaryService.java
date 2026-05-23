package com.youngstersclub.app.service;

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
    private final ConsumableService consumableService;
    private final KidsPlayService kidsPlayService;

    public UserPaymentSummaryService(
            FrameRepository frameRepository,
            ConsumableService consumableService,
            KidsPlayService kidsPlayService) {
        this.frameRepository = frameRepository;
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
        BigDecimal frameDue = (userId == null || selectedDate == null)
                ? BigDecimal.ZERO
                : frameRepository.getTotalDueForUserByDate(userId, selectedDate);
        BigDecimal consumableDue = consumableService.getConsumableDueByDate(userId, selectedDate);
        BigDecimal kidsDue = kidsPlayService.getKidsDueByDate(userId, selectedDate);
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
}
